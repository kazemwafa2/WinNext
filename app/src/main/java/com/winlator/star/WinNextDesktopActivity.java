package com.winlator.star;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.star.container.ContainerLauncher;
import com.winlator.star.core.AppUtils;
import com.winlator.star.xenvironment.ImageFs;
import com.winlator.star.xenvironment.ImageFsInstaller;

/**
 * WinNext desktop shell (Windows 11 inspired).
 *
 * IMPORTANT BEHAVIOR CHANGE (real execution, no fake boot):
 *  - onCreate runs REAL preparation: engine files check + automatic creation/repair of
 *    the shared default container ("WinNext Default"), with visible progress.
 *  - The blue "WinNext Apps" icon is wired to the SHARED execution service
 *    (ContainerLauncher) — the exact same service the Containers list Play button uses.
 *    Tapping it starts the real Windows environment (XServerDisplayActivity) or brings
 *    the already-running session back to the front. It never opens a demo page.
 */
public class WinNextDesktopActivity extends Activity {
    private FrameLayout root;
    private View menu;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean preparing = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        setContentView(root);
        prepareEnvironment();
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
        android.widget.ImageView iv = new android.widget.ImageView(this);
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

    /** Boot screen with a spinner and a status line (real work happens behind it). */
    private void bootScreen(String msg) {
        root.removeAllViews();
        menu = null;
        root.setBackgroundColor(BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LinearLayout bootBox = new LinearLayout(this);
        bootBox.setOrientation(LinearLayout.VERTICAL);
        bootBox.setGravity(Gravity.CENTER);
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(R.drawable.winnext_boot);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        bootBox.addView(iv, new LinearLayout.LayoutParams(-1, 0, 1));
        box.addView(bootBox, new LinearLayout.LayoutParams(-1, 0, 1));
        if (msg != null) {
            TextView t = text(msg, 15, Color.parseColor("#B8D8FF"));
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(24), 0, dp(24), dp(8));
            box.addView(t);
        }
        ProgressBar pb = new ProgressBar(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.setMargins(0, dp(8), 0, dp(28));
        box.addView(pb, lp);
        root.addView(box, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Setup-required screen: engine files are not installed yet. */
    private void setupNeededScreen() {
        root.removeAllViews();
        menu = null;
        root.setBackgroundColor(BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(32), 0, dp(32), 0);
        box.addView(logo(72));
        TextView t = text(getString(R.string.desktop_setup_needed), 18, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(16), 0, dp(8));
        box.addView(t);
        TextView d = text(getString(R.string.desktop_setup_needed_desc), 14, Color.parseColor("#B8D8FF"));
        d.setGravity(Gravity.CENTER);
        box.addView(d);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = dp(24);
        box.addView(button(getString(R.string.desktop_open_setup),
                Color.parseColor("#2B6CD9"), v -> openSetup()), bp);
        root.addView(box, new FrameLayout.LayoutParams(-1, -1));
    }

    /**
     * REAL preparation (replaces the former 3.5s fake boot delay):
     * 1. verify engine files (ImageFs) — if missing, point the user to the real setup;
     * 2. make sure the shared default container exists and is healthy.
     */
    private void prepareEnvironment() {
        if (preparing) return;
        preparing = true;
        bootScreen(getString(R.string.desktop_preparing_engine));

        ImageFs imageFs = ImageFs.find(this);
        if (!imageFs.isValid() || imageFs.getVersion() < ImageFsInstaller.LATEST_VERSION) {
            preparing = false;
            setupNeededScreen();
            return;
        }

        bootScreen(getString(R.string.desktop_preparing_container));
        ContainerLauncher.prepareDefaultContainer(this, new ContainerLauncher.Listener() {
            @Override public void onPreparing() { /* already showing progress */ }

            @Override public void onLaunchStarted(com.winlator.star.container.Container container) {
                preparing = false;
                // Default container is ready; the blue icon now launches it directly.
                showDesktop();
            }

            @Override public void onLaunchFailed(String errorMessageKey) {
                preparing = false;
                showDesktopWithWarning(errorMessageKey);
            }
        });
    }

    /** Desktop loads even if preparation failed — with an honest message, never a fake state. */
    private void showDesktopWithWarning(String errorMessageKey) {
        showDesktop();
        String msg;
        if ("engine_not_installed".equals(errorMessageKey)) {
            msg = getString(R.string.desktop_error_engine_missing);
        } else if ("recreate_failed".equals(errorMessageKey)) {
            msg = getString(R.string.desktop_error_recreate_failed);
        } else if ("create_failed".equals(errorMessageKey)) {
            msg = getString(R.string.desktop_error_create_failed);
        } else {
            msg = getString(R.string.launch_failed);
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void showDesktop() {
        root.removeAllViews();
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#00081A"), Color.parseColor("#0A3D91"), Color.parseColor("#2B8CFF")});
        root.setBackground(bg);

        // Desktop icon: directly runs the default container through the shared service.
        LinearLayout icon = new LinearLayout(this);
        icon.setOrientation(LinearLayout.VERTICAL);
        icon.setGravity(Gravity.CENTER);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.addView(logo(48));
        TextView lbl = text(getString(R.string.desktop_apps_label), 12, Color.WHITE);
        lbl.setGravity(Gravity.CENTER);
        icon.addView(lbl);
        icon.setOnClickListener(v -> runDefaultContainer());
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
        m.addView(text(getString(R.string.desktop_menu_subtitle), 12, Color.parseColor("#7CC4FF")));

        addMenuButton(m, getString(R.string.desktop_run_windows_apps), "run_default",
            Color.parseColor("#2B6CD9"));
        addMenuButton(m, getString(R.string.screen_containers), "containers", Color.parseColor("#303847"));
        addMenuButton(m, getString(R.string.screen_shortcuts), "shortcuts", Color.parseColor("#303847"));
        addMenuButton(m, getString(R.string.screen_settings), "settings", Color.parseColor("#303847"));
        addMenuButton(m, getString(R.string.screen_file_manager), "file_manager",
            Color.parseColor("#303847"));
        addMenuButton(m, getString(R.string.screen_personalization), "personalization", Color.parseColor("#303847"));
        addMenuButton(m, getString(R.string.screen_saves), "saves", Color.parseColor("#303847"));

        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams half =
            new LinearLayout.LayoutParams(0, -2, 1);
        half.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams half2 =
            new LinearLayout.LayoutParams(0, -2, 1);
        half2.setMargins(dp(5), 0, 0, 0);

        row.addView(button(getString(R.string.desktop_restart), Color.parseColor("#444444"),
            v -> restart()), half);
        row.addView(button(getString(R.string.desktop_shutdown), Color.parseColor("#C42B1C"),
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
                               String action, int color) {
        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(7);
        parent.addView(button(label, color, v -> {
            closeMenu();
            handleMenuAction(action);
        }), lp);
    }

    private void closeMenu() {
        if (menu != null) {
            root.removeView(menu);
            menu = null;
        }
    }

    /** Restart: a real application restart (engine session ends; data is kept on disk). */
    private void restart() {
        bootScreen(getString(R.string.desktop_restarting));
        h.postDelayed(() -> AppUtils.restartApplication(getApplicationContext()), 600);
    }

    /** Shutdown: really closes WinNext. Installed programs and files stay on disk. */
    private void shutdown() {
        bootScreen(getString(R.string.desktop_shutting_down));
        h.postDelayed(this::finishAffinity, 600);
    }

    private void handleMenuAction(String action) {
        switch (action) {
            case "run_default":
                runDefaultContainer();
                break;
            case "containers":
                launchRoute("containers");
                break;
            case "shortcuts":
                launchRoute("shortcuts");
                break;
            case "settings":
                launchRoute("settings");
                break;
            case "file_manager":
                launchRoute("file_manager");
                break;
            case "personalization":
                launchRoute("personalization");
                break;
            case "saves":
                launchRoute("saves");
                break;
            default:
                launchRoute("containers");
        }
    }

    private void launchRoute(String route) {
        Intent intent = new Intent(WinNextDesktopActivity.this, MainActivity.class);
        intent.putExtra("start_route", route);
        startActivity(intent);
    }

    /** The blue "WinNext Apps" icon AND the Start-menu entry share this exact path. */
    private void runDefaultContainer() {
        if (preparing) return;
        ContainerLauncher.launchDefaultContainer(this, new ContainerLauncher.Listener() {
            @Override public void onLaunchStarted(com.winlator.star.container.Container container) {
                // XServerDisplayActivity (singleTask) opens or restores the session.
            }
            @Override public void onLaunchFailed(String errorMessageKey) {
                if ("engine_not_installed".equals(errorMessageKey)) {
                    setupNeededScreen();
                } else {
                    Toast.makeText(WinNextDesktopActivity.this,
                            R.string.launch_failed, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** Opens the real setup flow owned by MainActivity (installs engine files). */
    private void openSetup() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("start_route", "containers");
        startActivity(intent);
    }
}
