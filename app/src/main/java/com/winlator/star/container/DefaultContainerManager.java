package com.winlator.star.container;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winlator.star.box64.Box64Preset;
import com.winlator.star.contents.ContentsManager;
import com.winlator.star.core.Callback;
import com.winlator.star.core.WineInfo;
import com.winlator.star.core.WineThemeManager;
import com.winlator.star.fexcore.FEXCorePreset;
import com.winlator.star.xenvironment.ImageFs;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the shared default container ("WinNext Default").
 *
 * The default container is a general-purpose container that is created automatically
 * on first use. Ordinary users never have to build a container or pick technical
 * settings such as Wine version, graphics driver, DX wrapper or audio driver.
 *
 * Guarantees:
 *  - Created at most once (idempotent; guarded by an in-flight flag and a marker flag
 *    stored in the container's extraData).
 *  - Survives app restarts: it is a normal on-disk container, only marked as default.
 *  - If its configuration file is missing but the Wine prefix is intact, the config is
 *    regenerated WITHOUT touching user files (real repair, no silent data loss).
 *  - If the Wine prefix itself is broken, a fresh prefix is created and the broken one
 *    is backed up (renamed) instead of deleted, so the caller can inform the user.
 */
public final class DefaultContainerManager {
    public static final String DEFAULT_CONTAINER_NAME = "WinNext Default";
    /** extraData marker stored inside the container's .container file. */
    public static final String MARKER_KEY = "winnextDefault";
    private static final String TAG = "DefaultContainer";

    public enum Status { READY, CREATED, REPAIRED_CONFIG, RECREATED, BUSY, FAILED }

    /** Result of an ensure operation. */
    public static class Result {
        public final Status status;
        public final Container container;
        public final String errorMessage;
        Result(Status status, Container container, String errorMessage) {
            this.status = status;
            this.container = container;
            this.errorMessage = errorMessage;
        }
    }

    private static final AtomicBoolean inFlight = new AtomicBoolean(false);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private DefaultContainerManager() {}

    /** True if the given container is (marked as) the default container. */
    public static boolean isDefaultContainer(Container container) {
        if (container == null) return false;
        if (DEFAULT_CONTAINER_NAME.equals(container.getName())) return true;
        return "1".equals(container.getExtra(MARKER_KEY, "0"));
    }

    /** Synchronous lookup of the default container (main thread is fine; list is in memory). */
    public static Container findDefault(ContainerManager manager) {
        ArrayList<Container> containers = manager.getContainers();
        // Prefer the explicit marker over the name so a user rename keeps it default.
        for (Container c : containers) if ("1".equals(c.getExtra(MARKER_KEY, "0"))) return c;
        for (Container c : containers) if (DEFAULT_CONTAINER_NAME.equals(c.getName())) return c;
        return null;
    }

    /**
     * Ensures the default container exists and is healthy. Safe to call as often as the
     * caller wants: concurrent and repeated calls collapse into one operation and never
     * create duplicate containers.
     *
     * @param callback called on the main thread with the result
     */
    public static void ensureDefaultContainer(Context context, ContentsManager contentsManager,
                                              Callback<Result> callback) {
        if (!inFlight.compareAndSet(false, true)) {
            callback.call(new Result(Status.BUSY, null, null));
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            Result result;
            try {
                result = ensureInternal(context, contentsManager);
            } catch (Exception e) {
                Log.e(TAG, "ensureDefaultContainer failed", e);
                result = new Result(Status.FAILED, null,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            } finally {
                inFlight.set(false);
            }
            final Result finalResult = result;
            main.post(() -> callback.call(finalResult));
        });
    }

    private static Result ensureInternal(Context context, ContentsManager contentsManager) {
        ImageFs imageFs = ImageFs.find(context);
        if (!imageFs.isValid()) {
            return new Result(Status.FAILED, null, "engine_not_installed");
        }

        ContainerManager manager = new ContainerManager(context);
        Container existing = findDefault(manager);
        if (existing != null) {
            Status status = verifyAndRepair(existing);
            switch (status) {
                case READY:
                    return new Result(Status.READY, existing, null);
                case REPAIRED_CONFIG:
                    existing.putExtra(MARKER_KEY, "1");
                    existing.saveData();
                    return new Result(Status.REPAIRED_CONFIG, existing, null);
                case RECREATED:
                    // Prefix was unusable: back it up and build a fresh one.
                    Container fresh = recreate(context, manager, contentsManager, existing);
                    if (fresh != null) return new Result(Status.RECREATED, fresh, null);
                    return new Result(Status.FAILED, null, "recreate_failed");
                default:
                    return new Result(Status.FAILED, existing, "unknown_state");
            }
        }

        // No default container yet -> create it once with sensible, stable defaults.
        Container created = createWithDefaults(context, manager, contentsManager);
        if (created != null) return new Result(Status.CREATED, created, null);
        return new Result(Status.FAILED, null, "create_failed");
    }

    /**
     * Health check of a default container.
     * READY           -> prefix and config are fine
     * REPAIRED_CONFIG -> config file was missing; caller regenerates it, user files untouched
     * RECREATED       -> the Wine prefix directory itself is unusable
     */
    private static Status verifyAndRepair(Container container) {
        File rootDir = container.getRootDir();
        if (rootDir == null || !rootDir.isDirectory()) return Status.RECREATED;

        File wineDir = new File(rootDir, ".wine");
        File driveC = new File(wineDir, "drive_c");
        boolean prefixOk = wineDir.isDirectory() && driveC.isDirectory();

        File configFile = container.getConfigFile();
        if (!configFile.isFile()) {
            if (!prefixOk) return Status.RECREATED;
            // Config lost but prefix healthy: regenerate config with defaults; the
            // container keeps its id/name/marker and ALL user files stay in place.
            return Status.REPAIRED_CONFIG;
        }
        if (!prefixOk) return Status.RECREATED;
        return Status.READY;
    }

    /** Builds the creation JSON with stable, engine-provided defaults. */
    private static JSONObject buildDefaultData() {
        JSONObject data = new JSONObject();
        try {
            data.put("name", DEFAULT_CONTAINER_NAME);
            data.put("screenSize", Container.DEFAULT_SCREEN_SIZE);
            data.put("envVars", Container.DEFAULT_ENV_VARS);
            data.put("graphicsDriver", Container.DEFAULT_GRAPHICS_DRIVER);
            data.put("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG);
            data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
            data.put("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG);
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("emulator", Container.DEFAULT_EMULATOR);
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES);
            data.put("showFPS", false);
            data.put("fpsCounterConfig", Container.DEFAULT_FPS_COUNTER_CONFIG);
            data.put("fullscreenStretched", false);
            data.put("inputType", 0);
            data.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
            data.put("box64Preset", Box64Preset.COMPATIBILITY);
            data.put("fexcorePreset", FEXCorePreset.INTERMEDIATE);
            data.put("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME);
            data.put("wineVersion", WineInfo.MAIN_WINE_VERSION.identifier());
            JSONObject extra = new JSONObject();
            extra.put(MARKER_KEY, "1");
            data.put("extraData", extra);
        } catch (Exception e) {
            Log.e(TAG, "buildDefaultData failed", e);
        }
        return data;
    }

    /** Blocking creation of the default container; must run on a background thread. */
    private static Container createWithDefaults(Context context, ContainerManager manager,
                                                ContentsManager contentsManager) {
        try {
            Container container = manager.createContainer(buildDefaultData(), contentsManager);
            if (container != null) {
                container.putExtra(MARKER_KEY, "1");
                container.saveData();
            }
            return container;
        } catch (Exception e) {
            Log.e(TAG, "createWithDefaults failed", e);
            return null;
        }
    }

    /**
     * Re-creates a broken default container. The old directory is backed up (renamed,
     * not deleted) so nothing is silently destroyed; the caller is told via
     * Status.RECREATED so it can inform the user that the old environment could not
     * be recovered.
     */
    private static Container recreate(Context context, ContainerManager manager,
                                      ContentsManager contentsManager, Container broken) {
        try {
            File rootDir = broken.getRootDir();
            if (rootDir != null && rootDir.isDirectory()) {
                File backup = new File(rootDir.getParentFile(),
                        rootDir.getName() + "-broken-" + System.currentTimeMillis());
                if (!rootDir.renameTo(backup)) return null;
            }
            // Remove the stale entry so createContainer does not collide with it.
            manager.getContainers().remove(broken);
            Container fresh = manager.createContainer(buildDefaultData(), contentsManager);
            if (fresh != null) {
                fresh.putExtra(MARKER_KEY, "1");
                fresh.saveData();
            }
            return fresh;
        } catch (Exception e) {
            Log.e(TAG, "recreate failed", e);
            return null;
        }
    }
}
