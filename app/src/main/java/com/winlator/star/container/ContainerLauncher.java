package com.winlator.star.container;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.winlator.star.R;
import com.winlator.star.XServerDisplayActivity;
import com.winlator.star.XrActivity;
import com.winlator.star.contents.ContentsManager;
import com.winlator.star.xenvironment.ImageFs;
import com.winlator.star.xenvironment.ImageFsInstaller;

/**
 * Single shared execution service used by BOTH the blue "WinNext Apps" icon on the
 * WinNext desktop and the Play buttons in the Containers list.
 *
 * Direct-launch flow (blue icon):
 *   check engine (ImageFs) -> ensure the default container exists / repair it ->
 *   start XServerDisplayActivity with the real container id.
 *
 * Guarantees:
 *  - The icon never opens a demo/simulated page: the same engine activity used by the
 *    Play button is started, with the same code path (including VR/XrActivity routing).
 *  - Repeated taps never create duplicate containers or duplicate launches: the
 *    launcher is guarded by an in-flight flag and XServerDisplayActivity itself is
 *    singleTask, so a running session is simply brought back to the front.
 *  - On failure the user sees a real message; nothing pretends to be running.
 */
public final class ContainerLauncher {
    private static final Object launchLock = new Object();
    private static boolean launchInProgress = false;

    private ContainerLauncher() {}

    /** Listener for the default-container launch flow. All methods arrive on the main thread. */
    public interface Listener {
        /** Preparation started (engine check / container creation). */
        default void onPreparing() {}
        /** The environment is now starting or was already running. */
        default void onLaunchStarted(Container container) {}
        /** Preparation failed; errorMessageKey is a raw cause (never user-facing). */
        default void onLaunchFailed(String errorMessageKey) {}
    }

    /**
     * Launch path used by the Containers list Play button: the container already
     * exists, so start it directly — identical behavior to before, now centralized
     * here so the blue icon and the Play button share one execution service.
     */
    public static boolean launchContainer(Context context, int containerId) {
        if (containerId <= 0) return false;
        synchronized (launchLock) {
            if (launchInProgress) {
                Toast.makeText(context, R.string.launch_already_starting, Toast.LENGTH_SHORT).show();
                return false;
            }
            launchInProgress = true;
        }
        try {
            Intent intent;
            if (!XrActivity.isEnabled(context)) {
                intent = new Intent(context, XServerDisplayActivity.class);
                intent.putExtra("container_id", containerId);
            } else {
                // VR session path — preserved from the original Containers screen.
                intent = null;
                if (context instanceof Activity) {
                    XrActivity.openIntent((Activity) context, containerId, null);
                } else {
                    return false;
                }
            }
            if (intent != null) {
                if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return true;
        } finally {
            synchronized (launchLock) {
                // XServerDisplayActivity is singleTask; the guard only needs to cover
                // the window before startActivity takes effect.
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.postDelayed(() -> launchInProgress = false, 1500);
            }
        }
    }

    /** Same as launchContainer but for an already-loaded Container object. */
    public static boolean launchContainer(Context context, Container container) {
        return container != null && launchContainer(context, container.id);
    }

    /**
     * Preparation-only entry point (used when the WinNext desktop boots): ensures the
     * default container exists and is healthy, but does NOT start the environment.
     * The user still launches it explicitly with the blue icon.
     */
    public static void prepareDefaultContainer(Context context, Listener listener) {
        ensureThen(context, false, listener);
    }

    /**
     * Direct-launch entry point for the blue "WinNext Apps" icon.
     * Ensures the default container, then starts it. Never shows fake content:
     * on any failure the listener (or a toast) reports the real cause.
     */
    public static void launchDefaultContainer(Context context, Listener listener) {
        ensureThen(context, true, listener);
    }

    private static void ensureThen(Context context, final boolean launchAfter, Listener listener) {
        ImageFs imageFs = ImageFs.find(context);
        if (!imageFs.isValid() || imageFs.getVersion() < ImageFsInstaller.LATEST_VERSION) {
            // Engine files are not ready yet — MainActivity owns the installation flow.
            if (listener != null) listener.onLaunchFailed("engine_not_installed");
            else Toast.makeText(context, R.string.launch_engine_not_ready, Toast.LENGTH_LONG).show();
            return;
        }

        if (listener != null) listener.onPreparing();

        ContentsManager contentsManager = new ContentsManager(context);
        contentsManager.syncContents();

        DefaultContainerManager.ensureDefaultContainer(context, contentsManager,
                new com.winlator.star.core.Callback<DefaultContainerManager.Result>() {
                    @Override
                    public void call(DefaultContainerManager.Result result) {
                        if (result.status == DefaultContainerManager.Status.BUSY) {
                            Toast.makeText(context, R.string.launch_already_starting, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (result.container == null || result.status == DefaultContainerManager.Status.FAILED) {
                            if (listener != null) listener.onLaunchFailed(
                                    result.errorMessage != null ? result.errorMessage : "create_failed");
                            else Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (result.status == DefaultContainerManager.Status.RECREATED) {
                            // The old prefix could not be recovered; tell the user honestly.
                            Toast.makeText(context, R.string.launch_container_recreated, Toast.LENGTH_LONG).show();
                        }
                        if (listener != null) listener.onLaunchStarted(result.container);
                        if (launchAfter) launchContainer(context, result.container);
                    }
                });
    }
}
