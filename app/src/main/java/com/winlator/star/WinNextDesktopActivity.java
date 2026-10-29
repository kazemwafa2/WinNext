package com.winlator.star;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

public class WinNextDesktopActivity extends Activity {
    private FrameLayout root;
    private View menu;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        setContentView(root);
        showBoot();
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onBackPressed() {
        if (menu != null) closeMenu();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private static final int BG = Color.rgb(0, 8, 26);

    private View logo(int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.winnext_icon);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    private TextView text(String t, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        return tv;
    }

    private TextView button(String t, int bg, View.OnClickListener l) {
        TextView tv = text(t, 16, Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(12), dp(16), dp(12));
        tv.setBackground(shape(bg, 8));
        tv.setOnClickListener(l);
        return tv;
    }

    private void centerScreen(String msg) {
        root.removeAllViews();
        menu = null;
        root.setBackgroundColor(BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ProgressBar pb = new ProgressBar(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.setMargins(0, dp(16), 0, dp(24));
        if (msg == null) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(R.drawable.winnext_boot);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(iv, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            box.addView(logo(72));
            TextView t = text(msg, 20, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(16), 0, 0);
            box.addView(t);
        }
        box.addView(pb, lp);
        root.addView(box, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showBoot() {
        centerScreen(null);
        h.postDelayed(this::showDesktop, 3500);
    }

    private void showDesktop() {
        root.removeAllViews();
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#00081A"), Color.parseColor("#0A3D91"), Color.parseColor("#2B8CFF")});
        root.setBackground(bg);

        // Desktop icon: opens the Windows engine (Winlator)
        LinearLayout icon = new LinearLayout(this);
        icon.setOrientation(LinearLayout.VERTICAL);
        icon.setGravity(Gravity.CENTER);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.addView(logo(48));
        TextView lbl = text("WinNext Apps", 12, Color.WHITE);
        lbl.setGravity(Gravity.CENTER);
        icon.addView(lbl);
        icon.setOnClickListener(v -> openEngine());
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(96), -2);
        ip.setMargins(dp(24), dp(40), 0, 0);
        root.addView(icon, ip);

        // Taskbar
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(Color.parseColor("#E61F1F1F"));
        View start = logo(26);
        FrameLayout startWrap = new FrameLayout(this);
        startWrap.addView(start, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        startWrap.setOnClickListener(v -> toggleMenu());
        bar.addView(startWrap, new FrameLayout.LayoutParams(dp(56), -1, Gravity.CENTER));
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a\nM/d/yyyy");
        clock.setFormat24Hour("HH:mm\nM/d/yyyy");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(11);
        clock.setGravity(Gravity.END);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        cp.rightMargin = dp(16);
        bar.addView(clock, cp);
        root.addView(bar, new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM));
    }

    private void toggleMenu() {
        if (menu != null) {
            closeMenu();
            return;
        }

        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setPadding(dp(18), dp(18), dp(18), dp(18));
        m.setBackground(shape(Color.parseColor("#F0202028"), 16));

        m.addView(text("WinNext", 22, Color.WHITE));
        m.addView(text("Your desktop", 12, Color.parseColor("#7CC4FF")));

        addMenuButton(m, "Run Windows apps  ·  Winlator", "containers",
            Color.parseColor("#2B6CD9"));
        addMenuButton(m, "Containers", "containers", Color.parseColor("#303847"));
        addMenuButton(m, "Shortcuts", "shortcuts", Color.parseColor("#303847"));
        addMenuButton(m, "Settings", "settings", Color.parseColor("#303847"));
        addMenuButton(m, "This PC  ·  File Manager", "file_manager",
            Color.parseColor("#303847"));
        addMenuButton(m, "Appearance", "appearance", Color.parseColor("#303847"));
        addMenuButton(m, "Saves", "saves", Color.parseColor("#303847"));

        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams half =
            new LinearLayout.LayoutParams(0, -2, 1);
        half.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams half2 =
            new LinearLayout.LayoutParams(0, -2, 1);
        half2.setMargins(dp(5), 0, 0, 0);

        row.addView(button("Restart", Color.parseColor("#444444"),
            v -> restart()), half);
        row.addView(button("Shut down", Color.parseColor("#C42B1C"),
            v -> shutdown()), half2);

        LinearLayout.LayoutParams rp =
            new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(10);
        m.addView(row, rp);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min(dp(360), screenWidth - dp(24));
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(
            panelWidth, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        mp.bottomMargin = dp(56);
        root.addView(m, mp);
        menu = m;
    }

    private void addMenuButton(LinearLayout parent, String label,
                               String route, int color) {
        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(7);
        parent.addView(button(label, color, v -> {
            closeMenu();
            launchRoute(route);
        }), lp);
    }

    private void closeMenu() {
        if (menu != null) {
            root.removeView(menu);
            menu = null;
        }
    }

    private void restart() {
        centerScreen("Restarting WinNext");
        h.postDelayed(this::showBoot, 800);
    }

    private void shutdown() {
        centerScreen("Shutting down");
        h.postDelayed(this::finishAndRemoveTask, 800);
    }

    private void launchRoute(String route) {
        Intent intent = new Intent(WinNextDesktopActivity.this, MainActivity.class);
        intent.putExtra("start_route", route);
        startActivity(intent);
    }

    private void openEngine() {
        launchRoute("containers");
    }

}
