package com.example.graphhopper;

import android.content.Context;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.storage.DAType;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;

public class GraphHopperManager {

    private static GraphHopperManager instance;
    private GraphHopper hopper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isInitialized = false;

    private GraphHopperManager() {
    }

    public static synchronized GraphHopperManager getInstance() {
        if (instance == null) {
            instance = new GraphHopperManager();
        }
        return instance;
    }

    public Future<Boolean> initialize(Context context, InitCallback callback) {
        return executor.submit(() -> {
            try {
                File osmFile = GraphHopperFileManager.getOsmFile(context);
                File cacheDir = GraphHopperFileManager.getGraphCacheDir(context);

                if (!osmFile.exists() || !cacheDir.exists()) {
                    GraphHopperFileManager.copyFromDownloadDirectory(context);
                }
                if (!osmFile.exists()) {
                    if (!GraphHopperFileManager.copyOsmFromAssets(context)) {
                        if (callback != null) callback.onError("无法复制 OSM 文件");
                        return false;
                    }
                }
                // 必须已有 graph-cache 才可加载；在手机上从 OSM 导入会 OOM，禁止尝试
                if (!cacheDir.exists() || !GraphHopperFileManager.hasGraphData(cacheDir)) {
                    if (callback != null) {
                        callback.onError("未找到 graph-cache。请在电脑上执行 adb push graph-cache /sdcard/Download/，并在应用中授予存储权限后重试。不可在手机上从 OSM 导入（会内存不足）。");
                    }
                    return false;
                }

                hopper = new GraphHopper();
                hopper.setOSMFile(osmFile.getAbsolutePath());
                hopper.setGraphHopperLocation(cacheDir.getAbsolutePath());
                // 与生成 graph-cache 时的 config 一致：config.yml 启用了 graph.elevation.provider: srtm，此处必须 true
                hopper.setElevation(true);

                // 与 graphhopper-master/config.yml 的 graph.encoded_values 严格一致，否则加载 graph-cache 会报错
                hopper.setEncodedValuesString(
                    "car_access, car_average_speed, road_class, roundabout, max_speed, "
                        + "foot_access, foot_average_speed, foot_priority, foot_road_access, average_slope, "
                        + "road_environment, max_height, max_weight, max_width, surface, orientation"
                );

                // universal_vehicle：与 config.yml 一致（distance_influence 70, car_average_speed, priority 1.0, turn_costs motor_vehicle 60）
                CustomModel vehicleModel = new CustomModel();
                vehicleModel.setDistanceInfluence(70.0);
                vehicleModel.addToPriority(If("true", MULTIPLY, "1.0"));
                vehicleModel.addToSpeed(If("true", LIMIT, "car_average_speed"));
                Profile vehicleProfile = new Profile("universal_vehicle").setCustomModel(vehicleModel)
                    .setTurnCostsConfig(new TurnCostsConfig(Collections.singletonList("motor_vehicle"), 60));

                // universal_soldier：与 config.yml 一致（distance_influence 0, foot_average_speed, priority 1.0，无 turn_costs）
                CustomModel soldierModel = new CustomModel();
                soldierModel.setDistanceInfluence(0.0);
                soldierModel.addToPriority(If("true", MULTIPLY, "1.0"));
                soldierModel.addToSpeed(If("true", LIMIT, "foot_average_speed"));

                hopper.setProfiles(vehicleProfile, new Profile("universal_soldier").setCustomModel(soldierModel));

                // 使用 MMAP 加载图，避免整图进堆导致 OOM；只读加载即可
                hopper.setDataAccessDefaultType(DAType.MMAP);
                hopper.setAllowWrites(false);
                hopper.importOrLoad();

                isInitialized = true;
                if (callback != null) {
                    callback.onSuccess();
                }
                return true;
            } catch (Throwable t) {
                t.printStackTrace();
                if (callback != null) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    if (t instanceof OutOfMemoryError) {
                        msg = "内存不足 (OOM)，请关闭其他应用或使用更大内存的模拟器";
                    }
                    callback.onError(msg);
                }
                return false;
            }
        });
    }

    public Future<GHResponse> route(
        String profile,
        double fromLat, double fromLon,
        double toLat, double toLon,
        CustomModel requestCustomModel
    ) {
        if (!isInitialized || hopper == null) {
            throw new IllegalStateException("GraphHopper 未初始化");
        }
        return executor.submit(() -> {
            GHRequest req = new GHRequest()
                .addPoint(new GHPoint(fromLat, fromLon))
                .addPoint(new GHPoint(toLat, toLon))
                .setProfile(profile)
                .setLocale(Locale.CHINESE);
            if (requestCustomModel != null) {
                req.setCustomModel(requestCustomModel);
            }
            return hopper.route(req);
        });
    }

    public Future<GHResponse> route(
        String profile,
        double fromLat, double fromLon,
        double toLat, double toLon,
        CustomModel requestCustomModel,
        boolean elevation,
        boolean pointsEncoded,
        boolean chDisable,
        List<String> pathDetails
    ) {
        if (!isInitialized || hopper == null) {
            throw new IllegalStateException("GraphHopper 未初始化");
        }
        return executor.submit(() -> {
            GHRequest req = new GHRequest()
                .addPoint(new GHPoint(fromLat, fromLon))
                .addPoint(new GHPoint(toLat, toLon))
                .setProfile(profile)
                .setLocale(Locale.CHINESE);
            if (requestCustomModel != null) {
                req.setCustomModel(requestCustomModel);
            }
            if (elevation) {
                req.putHint("elevation", true);
            }
            if (!pointsEncoded) {
                req.putHint("points_encoded", false);
            }
            if (chDisable) {
                req.putHint("ch.disable", true);
            }
            if (pathDetails != null && !pathDetails.isEmpty()) {
                req.setPathDetails(pathDetails);
            }
            return hopper.route(req);
        });
    }

    public void close() {
        executor.submit(() -> {
            if (hopper != null) {
                hopper.close();
                hopper = null;
            }
            isInitialized = false;
        });
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public interface InitCallback {
        void onSuccess();
        void onError(String error);
    }
}
