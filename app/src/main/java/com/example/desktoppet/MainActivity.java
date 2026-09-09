package com.example.desktoppet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView speedValue, sizeValue, opacityValue;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("pet", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);

        TextView title = new TextView(this);
        title.setText("乾恋万康桌宠");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView by = new TextView(this);
        by.setText("由 promise 研发");
        by.setTextSize(14);
        by.setGravity(Gravity.CENTER);
        by.setPadding(0, 6, 0, 24);
        root.addView(by);

        TextView intro = new TextView(this);
        intro.setText("透明背景 · 自动乱跑 · 点击跳跃 · 长按互动 · 可拖动\n\n桌宠会在屏幕上自动行走，碰到边缘会转身。长按小人可以打开互动菜单。");
        intro.setTextSize(16);
        intro.setPadding(0, 8, 0, 20);
        root.addView(intro);

        addSetting(root, "移动速度", 1, 10, prefs.getInt("speed", 5), "speed", " / 10");
        speedValue = (TextView) root.getChildAt(root.getChildCount()-1);
        addSetting(root, "大小", 80, 180, prefs.getInt("size", 125), "size", " dp");
        sizeValue = (TextView) root.getChildAt(root.getChildCount()-1);
        addSetting(root, "透明度", 30, 100, prefs.getInt("opacity", 100), "opacity", "%");
        opacityValue = (TextView) root.getChildAt(root.getChildCount()-1);

        Button start = new Button(this); start.setText("启动 / 重启桌宠"); root.addView(start);
        Button stop = new Button(this); stop.setText("关闭桌宠"); root.addView(stop);

        TextView note = new TextView(this);
        note.setText("互动方式：\n• 点击：跳一下\n• 长按：打开互动菜单\n• 拖动：把桌宠拽到任意位置\n• 菜单：暂停、跳跃、睡觉/醒来、设置、退出\n\n如果后台被系统清理，请把本应用加入系统的后台/自启动白名单。");
        note.setTextSize(14);
        note.setPadding(0, 24, 0, 0);
        root.addView(note);

        start.setOnClickListener(v -> startPet());
        stop.setOnClickListener(v -> stopService(new Intent(this, PetService.class)));
        setContentView(root);
    }

    private void addSetting(LinearLayout root, String label, int min, int max, int value, String key, String suffix) {
        TextView l = new TextView(this); l.setText(label); l.setTextSize(16); root.addView(l);
        SeekBar bar = new SeekBar(this); bar.setMax(max-min); bar.setProgress(value-min); root.addView(bar);
        TextView val = new TextView(this); val.setTextSize(14); val.setText(value + suffix); root.addView(val);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                int v=p+min; prefs.edit().putInt(key,v).apply(); val.setText(v+suffix);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
    }

    private void startPet() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(i); return;
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        Intent i = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "桌宠已启动", Toast.LENGTH_SHORT).show();
    }
}
