package com.example.desktoppet;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.Random;

public class PetService extends Service {
    private WindowManager wm;
    private ImageView pet;
    private WindowManager.LayoutParams lp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // 0~4: 向右走；5~9: 向左走。两组都是真实的不同步态，不再靠 scaleX 镜像。
    private final int[] walkRight = {
        R.drawable.pet_walk_0, R.drawable.pet_walk_1, R.drawable.pet_walk_2,
        R.drawable.pet_walk_3, R.drawable.pet_walk_4
    };
    private final int[] walkLeft = {
        R.drawable.pet_walk_5, R.drawable.pet_walk_6, R.drawable.pet_walk_7,
        R.drawable.pet_walk_8, R.drawable.pet_walk_9
    };
    private final int[] idle = {
        R.drawable.pet_idle_6, R.drawable.pet_idle_7, R.drawable.pet_idle_8, R.drawable.pet_idle_9
    };

    private float fx = 60, fy = 300;
    private float targetX, targetY;
    private boolean moving = true, dragging = false, sleeping = false;
    private boolean facingRight = true;
    private int frame = 0, idleFrame = 0;
    private long downAt, stateUntil = 0, lastFrameAt = 0;
    private int lastRawX, lastRawY;
    private View menu;
    private SharedPreferences prefs;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private int petSize() { return dp(prefs.getInt("size", 125)); }
    private int screenW() { return wm.getCurrentWindowMetrics().getBounds().width(); }
    private int screenH() { return wm.getCurrentWindowMetrics().getBounds().height(); }

    private float minY() { return dp(35); }
    private float maxY() { return Math.max(minY(), screenH() - pet.getHeight() - dp(45)); }
    private float maxX() { return Math.max(0, screenW() - pet.getWidth()); }

    private void chooseTarget() {
        targetX = dp(10) + random.nextFloat() * Math.max(1, maxX() - dp(20));
        targetY = minY() + random.nextFloat() * Math.max(1, maxY() - minY());
    }

    private void setImage(int res) {
        if (pet != null) pet.setImageResource(res);
    }

    private void animateWalk(long now) {
        if (now - lastFrameAt < 115) return;
        lastFrameAt = now;
        int[] seq = facingRight ? walkRight : walkLeft;
        frame = (frame + 1) % seq.length;
        setImage(seq[frame]);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (pet == null) return;
            long now = System.currentTimeMillis();

            if (!dragging) {
                if (sleeping) {
                    if (now >= stateUntil) {
                        sleeping = false;
                        moving = true;
                        chooseTarget();
                        setImage(idle[0]);
                    }
                } else if (moving && now >= stateUntil) {
                    int speedSetting = Math.max(1, Math.min(10, prefs.getInt("speed", 5)));
                    // 每秒约 80~260dp，明显是走路而不是贴着地面缓慢平移。
                    float speed = dp(75 + speedSetting * 20) / 1000f;
                    float dx = targetX - fx, dy = targetY - fy;
                    float dist = (float)Math.sqrt(dx * dx + dy * dy);

                    if (dist < dp(8)) {
                        fx = targetX; fy = targetY;
                        lp.x = Math.round(fx); lp.y = Math.round(fy);
                        safeUpdate();
                        if (random.nextInt(100) < 45) {
                            moving = false;
                            stateUntil = now + 700 + random.nextInt(2200);
                            idleFrame = random.nextInt(idle.length);
                            setImage(idle[idleFrame]);
                        } else {
                            chooseTarget();
                        }
                    } else {
                        float step = Math.min(speed * 80f, dist); // 80ms tick
                        fx += dx / dist * step;
                        fy += dy / dist * step;
                        fx = Math.max(0, Math.min(fx, maxX()));
                        fy = Math.max(minY(), Math.min(fy, maxY()));

                        if (Math.abs(dx) > dp(2)) facingRight = dx > 0;
                        lp.x = Math.round(fx);
                        lp.y = Math.round(fy);
                        safeUpdate();
                        animateWalk(now);
                    }
                }
            }
            handler.postDelayed(this, 80);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("pet", MODE_PRIVATE);
        startNotice();
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        pet = new ImageView(this);
        pet.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pet.setAlpha(prefs.getInt("opacity", 100) / 100f);
        setImage(walkRight[0]);
        pet.setOnTouchListener((v, e) -> onTouch(e));

        int s = petSize();
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
            s, s, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        fx = Math.max(0, (screenW() - s) * 0.45f);
        fy = Math.max(minY(), (screenH() - s) * 0.65f);
        lp.x = Math.round(fx); lp.y = Math.round(fy);
        chooseTarget();
        try { wm.addView(pet, lp); } catch (Exception e) { stopSelf(); return; }
        stateUntil = System.currentTimeMillis() + 500;
        handler.post(tick);
    }

    private void safeUpdate() {
        try { wm.updateViewLayout(pet, lp); } catch (Exception ignored) {}
    }

    private boolean onTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                moving = false;
                downAt = System.currentTimeMillis();
                lastRawX = (int)e.getRawX(); lastRawY = (int)e.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                int nx = (int)e.getRawX(), ny = (int)e.getRawY();
                fx += nx - lastRawX; fy += ny - lastRawY;
                fx = Math.max(0, Math.min(fx, maxX()));
                fy = Math.max(minY(), Math.min(fy, maxY()));
                lp.x = Math.round(fx); lp.y = Math.round(fy);
                lastRawX = nx; lastRawY = ny;
                safeUpdate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                long held = System.currentTimeMillis() - downAt;
                if (held >= 550) showMenu();
                else jump();
                return true;
        }
        return false;
    }

    private void jump() {
        if (pet == null) return;
        moving = false; sleeping = false;
        final float baseY = fy;
        final long start = System.currentTimeMillis();
        final Runnable r = new Runnable() {
            @Override public void run() {
                if (pet == null) return;
                long t = System.currentTimeMillis() - start;
                if (t < 360) {
                    double p = t / 360.0;
                    fy = baseY - dp((int)(72 * Math.sin(Math.PI * p)));
                    lp.y = Math.round(fy); safeUpdate();
                    handler.postDelayed(this, 20);
                } else {
                    fy = baseY; lp.y = Math.round(fy); safeUpdate();
                    chooseTarget(); moving = true;
                    stateUntil = System.currentTimeMillis() + 200;
                }
            }
        };
        handler.post(r);
    }

    private void showMenu() {
        if (menu != null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackgroundColor(0xEEFFFFFF);
        TextView title = new TextView(this);
        title.setText("乾恋万康桌宠 · 互动"); title.setTextSize(18);
        title.setPadding(dp(8), dp(6), dp(8), dp(8)); box.addView(title);
        addMenuButton(box, moving ? "暂停移动" : "继续移动", () -> { moving = !moving; if (moving) chooseTarget(); closeMenu(); });
        addMenuButton(box, "跳一下", () -> { closeMenu(); jump(); });
        addMenuButton(box, sleeping ? "醒来" : "睡觉", () -> { sleeping = !sleeping; moving = !sleeping; if (sleeping) { stateUntil = System.currentTimeMillis()+8000; setImage(idle[2]); } else { chooseTarget(); setImage(idle[0]); } closeMenu(); });
        addMenuButton(box, "设置", () -> { closeMenu(); Intent i = new Intent(this, MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); });
        addMenuButton(box, "退出桌宠", () -> { closeMenu(); stopSelf(); });
        menu = box;
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams mp = new WindowManager.LayoutParams(dp(205), WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        mp.gravity = Gravity.TOP | Gravity.START;
        mp.x = Math.max(0, Math.min(lp.x, screenW()-dp(205))); mp.y = Math.max(dp(20), lp.y-dp(230));
        try { wm.addView(menu, mp); } catch (Exception e) { menu = null; }
    }

    private void addMenuButton(LinearLayout box, String text, final Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v -> action.run());
        box.addView(b, new LinearLayout.LayoutParams(-1, dp(45)));
    }

    private void closeMenu() { if (menu != null) { try { wm.removeView(menu); } catch (Exception ignored) {} menu = null; } }

    private void startNotice() {
        String ch = "desktop_pet";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(ch, "桌宠运行中", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this,ch) : new Notification.Builder(this);
        Notification n = b.setContentTitle("乾恋万康桌宠正在活动").setContentText("点击跳跃，长按互动，拖动可以移动").setSmallIcon(android.R.drawable.ic_menu_compass).build();
        startForeground(1, n);
    }

    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); closeMenu(); if (pet != null) try { wm.removeView(pet); } catch (Exception ignored) {} super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
