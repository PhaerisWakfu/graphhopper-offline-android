package com.example.graphhopper;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.PointList;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView tvStatus;
    private TextView tvResult;

    private static final int REQUEST_STORAGE = 1;
    private static final int REQUEST_ALL_FILES = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tv_status);
        tvResult = findViewById(R.id.tv_result);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                setStatus("请授予「所有文件」权限，以便从「下载」读取地图数据");
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(i, REQUEST_ALL_FILES);
                } catch (Exception e) {
                    setStatus("请在系统设置中为本应用开启「所有文件」或存储权限");
                    initGraphHopper();
                }
                return;
            }
        } else if (needStoragePermission()) {
            setStatus("请允许存储权限以从「下载」目录读取地图数据");
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_STORAGE);
            return;
        }
        initGraphHopper();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALL_FILES) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                setStatus("权限已授予，正在初始化...");
            } else {
                setStatus("未授予「所有文件」权限，将尝试从 assets 或已有数据加载");
            }
            initGraphHopper();
        }
    }

    private boolean needStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initGraphHopper();
            } else {
                setStatus("未授予存储权限，将尝试从 assets 或已有数据加载");
                initGraphHopper();
            }
        }
    }

    private void initGraphHopper() {
        GraphHopperManager manager = GraphHopperManager.getInstance();
        if (manager.isInitialized()) {
            setStatus("已初始化，开始算路");
            calculateRoute();
            return;
        }

        setStatus("正在初始化（首次约需 1～3 分钟，请勿退出）...");

        executor.execute(() -> {
            Runnable remindStillLoading = () -> mainHandler.post(() -> setStatus("仍在加载图数据，请耐心等待..."));
            mainHandler.postDelayed(remindStillLoading, 25000);
            try {
                Future<Boolean> future = manager.initialize(MainActivity.this, new GraphHopperManager.InitCallback() {
                    @Override
                    public void onSuccess() {
                        mainHandler.removeCallbacks(remindStillLoading);
                        mainHandler.post(() -> {
                            setStatus("初始化完成");
                            Toast.makeText(MainActivity.this, "初始化完成", Toast.LENGTH_SHORT).show();
                            calculateRoute();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        mainHandler.removeCallbacks(remindStillLoading);
                        mainHandler.post(() -> {
                            setStatus("初始化失败: " + error);
                            Toast.makeText(MainActivity.this, "初始化失败: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
                future.get();
            } catch (Throwable e) {
                mainHandler.removeCallbacks(remindStillLoading);
                String msg = e.getMessage();
                if (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null) {
                    Throwable cause = e.getCause();
                    msg = cause instanceof OutOfMemoryError
                        ? "内存不足 (OOM)，请关闭其他应用或使用更大内存的模拟器"
                        : (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
                } else if (msg == null) {
                    msg = e.getClass().getSimpleName();
                }
                final String showMsg = msg;
                mainHandler.post(() -> {
                    setStatus("初始化异常: " + showMsg);
                    Toast.makeText(MainActivity.this, "初始化异常: " + showMsg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void calculateRoute() {
        GraphHopperManager manager = GraphHopperManager.getInstance();
        StringBuilder sb = new StringBuilder();

        setStatus("正在算路（可能需数十秒，请稍候）...");
        tvResult.setText("");

        executor.execute(() -> {
            try {
                // 示例：短距离两点（约 5km），避免 CustomModel+无 CH 时大图全图搜索导致卡死
                double fromLon = 112.38801;
                double fromLat = 34.400409;
                double toLon = 111.575021;
                double toLat = 33.599139;

                // 与 JSON 示例一致的 custom_model（Android 使用解释执行）
                CustomModel customModel = new CustomModel();
                customModel.addToPriority(If("max_weight < 72.0", MULTIPLY, "0"));
                customModel.addToPriority(If("max_height < 3.0", MULTIPLY, "0"));
                customModel.addToPriority(If("max_width < 4.0", MULTIPLY, "0"));
                customModel.addToPriority(If("road_environment == TUNNEL", MULTIPLY, "0"));
                customModel.addToPriority(If("road_class == RESIDENTIAL || road_class == LIVING_STREET", MULTIPLY, "0.1"));
                customModel.addToPriority(If("road_class == MOTORWAY", MULTIPLY, "0.4"));
                customModel.addToPriority(If("surface == DIRT", MULTIPLY, "0.1"));
                customModel.addToPriority(If("surface == SAND", MULTIPLY, "0"));
                customModel.addToSpeed(If("true", LIMIT, "60"));
                customModel.addToSpeed(If("road_class == PRIMARY || road_class == TRUNK", LIMIT, "50"));
                customModel.addToSpeed(If("surface == DIRT || surface == GRASS", LIMIT, "45"));
                customModel.addToSpeed(If("road_class == TERTIARY || road_class == UNCLASSIFIED", MULTIPLY, "0.6"));

                List<String> details = Arrays.asList(
                    "road_environment", "road_class", "surface",
                    "average_slope", "max_speed", "max_weight", "max_height"
                );

                // 90 秒超时，避免无 CH 时长时间卡死
                long startMs = System.currentTimeMillis();
                Future<GHResponse> routeFuture = manager.route(
                    "universal_vehicle",
                    fromLat, fromLon, toLat, toLon,
                    customModel,
                    true,   // elevation
                    false,  // points_encoded
                    true,   // ch.disable = true（禁用 CH，算路会变慢）
                    details
                );
                GHResponse response = routeFuture.get(90, TimeUnit.SECONDS);
                long computationMs = System.currentTimeMillis() - startMs;

                mainHandler.post(() -> {
                    setStatus("算路完成");
                    appendRouteResult(sb, response, "你的示例路线", computationMs);
                    tvResult.setText(sb.toString());
                });
            } catch (TimeoutException e) {
                mainHandler.post(() -> {
                    setStatus("算路超时");
                    String msg = "算路超过 90 秒未完成。长距离 + CustomModel 时通常无法用 CH，会极慢。请改用短距离或先测无 CustomModel 的 profile。";
                    sb.append(msg).append("\n");
                    tvResult.setText(sb.toString());
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setStatus("算路失败");
                    sb.append("算路失败: ").append(e.getMessage()).append("\n");
                    e.printStackTrace();
                    tvResult.setText(sb.toString());
                    Toast.makeText(MainActivity.this, "算路失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void appendRouteResult(StringBuilder sb, GHResponse response, String label, long computationMs) {
        if (response.hasErrors()) {
            sb.append(label).append(" 失败: ").append(response.getErrors()).append("\n\n");
            return;
        }
        ResponsePath path = response.getBest();
        PointList points = path.getPoints();
        double distance = path.getDistance();
        long time = path.getTime();
        int pointCount = points.size();
        sb.append(label).append(":\n");
        sb.append("  计算耗时: ").append(computationMs).append(" ms\n");
        sb.append("  距离: ").append(String.format("%.0f", distance)).append(" m\n");
        sb.append("  时间: ").append(time).append(" ms\n");
        sb.append("  点数: ").append(pointCount).append("\n");
        
        if (pointCount > 0) {
            double startEle = points.getEle(0);
            double endEle = points.getEle(pointCount - 1);
            if (!Double.isNaN(startEle) && !Double.isNaN(endEle)) {
                sb.append("  起点高程: ").append(String.format("%.1f", startEle)).append(" m\n");
                sb.append("  终点高程: ").append(String.format("%.1f", endEle)).append(" m\n");
            }
        }
        
        // 显示 path details（如果有）
        if (path.getPathDetails() != null && !path.getPathDetails().isEmpty()) {
            sb.append("\n  Path Details:\n");
            path.getPathDetails().forEach((key, details) -> {
                sb.append("    ").append(key).append(": ").append(details.size()).append(" segments\n");
            });
        }
        sb.append("\n");
    }

    private void setStatus(String msg) {
        if (tvStatus != null) {
            tvStatus.setText(msg);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
