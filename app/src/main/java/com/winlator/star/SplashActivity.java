package com.winlator.star;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/** WinNext boot screen. Shows the logo and a loader, then opens MainActivity. */
public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.rgb(0, 8, 26));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.winnext_boot);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(logo, new LinearLayout.LayoutParams(-1, 0, 1f));

        ProgressBar pb = new ProgressBar(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (36 * d), (int) (36 * d));
        lp.setMargins(0, (int) (8 * d), 0, (int) (32 * d));
        box.addView(pb, lp);

        setContentView(box);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Short brand display only — the real (previously faked) work of preparing the
        // engine and the default container now happens inside WinNextDesktopActivity.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, WinNextDesktopActivity.class));
            finish();
        }, 700);
    }
}
