# GraphHopper Android 离线路径

在 Android 上使用 GraphHopper 12.0-SNAPSHOT 做离线路径规划。以下为**本地运行保姆教程**，从环境准备到算路结果一步到位。

---

## 第一步：准备电脑环境

### 1.1 安装 JDK

- 本机可装 **JDK 25** 跑 GraphHopper；但 **Android Studio 里用于 Gradle 的 JDK** 建议用 **17 或 21**（Gradle 9 最高只支持到 JVM 24，不能用 25）。
- 若用命令行 Gradle，请确保 `java -version` 为 11+。

### 1.2 安装 Android Studio

1. 打开：<https://developer.android.com/studio>
2. 下载并安装，安装时勾选：
   - **Android SDK**
   - **Android Virtual Device**（模拟器）
3. 首次启动时按向导下载一个 **Android SDK** 和至少一个 **系统镜像**（建议选 **API 30** 或 **API 34**，如 “Tiramisu” 或 “UpsideDownCake”）。

---

## 第二步：准备 GraphHopper 依赖（12.0-SNAPSHOT）

本项目依赖 `com.graphhopper:graphhopper-core:12.0-SNAPSHOT`，该版本**不在 Maven Central**，需要来自**本地 Maven 仓库**。

### 方式 A：你本机已有 GraphHopper 12.0 源码或工程

在 GraphHopper 项目根目录执行：

```bash
# Maven 项目
mvn install -DskipTests

# 或 Gradle 项目
./gradlew publishToMavenLocal
```

这样会把 `graphhopper-core:12.0-SNAPSHOT` 安装到本机 `~/.m2/repository`（或 Gradle 的 local repo）。Android 工程的根 `build.gradle` 里已配置 `mavenLocal()`，同步时会从这里解析。

### 方式 B：本机没有 GraphHopper 源码

1. 克隆官方仓库并构建：
   ```bash
   git clone https://github.com/graphhopper/graphhopper.git
   cd graphhopper
   ./gradlew publishToMavenLocal -x test
   ```
2. 或从你**已能跑通 /route 的那台机器**上，把 `~/.m2/repository/com/graphhopper`（或 Gradle 的 `graphhopper-core` 目录）拷到本机对应路径，再在 Android Studio 里同步。

**重要**：必须用 **JDK 21（或 17）** 来执行上述 Maven/Gradle 构建，不要用 JDK 25。否则 class 版本过高，Android 会报 **“类文件具有错误的版本 69.0, 应为 65.0”**。本 Android 项目已配置为 **Java 21** 编译（与 GraphHopper 21 一致），若你 GraphHopper 用 21 构建，两边保持一致即可。

**验证**：同步完成后，在 Android Studio 的 **Project** 视图中，展开 **External Libraries**，应能看到 `graphhopper-core-12.0-SNAPSHOT`。

---

## 第三步：用 Android Studio 打开项目

1. 启动 **Android Studio**。
2. **File → Open**，选择 **本项目的根目录**（即包含 `build.gradle` 和 `app` 文件夹的那一层，例如 `d:\workspace\graphhopper-android`）。
3. 若提示 “Trust Project”，选 **Trust**。
4. 若弹出 **“Select SDKs” / “Please provide the path to the Android SDK”**：
   - 点 **Browse**，选的是 **Android SDK 的根目录**，不是随便一个“安卓”文件夹。
   - **正确路径**：打开后**直接能看到** `build-tools`、`platforms`、`platform-tools` 这几个子文件夹的那一层。常见位置示例：
     - `C:\Users\你的用户名\AppData\Local\Android\Sdk`
     - 或安装 Android Studio 时你自定义的 SDK 目录。
   - 若选成上一级（例如只选了 `Android` 或 `Sdk` 的父目录），会报 **“The selected directory is not a valid home for Android SDK”**，请再选一次，选到**包含 `platforms` 和 `build-tools` 的那一层**。
5. 等待右下角 **Gradle Sync** 完成（首次可能下载 Gradle 与依赖，需几分钟）。
6. **若报 “Could not load wrapper properties” 或 “Minimum supported Gradle version is 9.1.0”（当前 9.0）**：本项目不再使用项目内的 Gradle wrapper，请改用「指定本机 Gradle」方式，一步到位：
   - **(1)** 在项目根目录下手动删掉整个 **gradle** 文件夹（若有），不要保留 wrapper。
   - **(2)** 下载 **Gradle 9.1.0**：浏览器打开 <https://services.gradle.org/distributions/gradle-9.1.0-bin.zip>，下载后解压到任意目录（例如 `D:\program\gradle-9.1.0`），得到文件夹 **gradle-9.1.0**（其内有 `bin`、`lib` 等）。
   - **(3)** 在 Android Studio：**File → Settings → Build, Execution, Deployment → Build Tools → Gradle**。在 **Gradle** 区域：选 **Use Gradle from: Specified location**，路径填刚才解压的目录（如 `D:\program\gradle-9.1.0`）。**Gradle JDK** 选 **JDK 17** 或 **21**（不要选 1.8 或 25）。点 **Apply → OK**。
   - **(4)** 再点 **Sync Project with Gradle Files**。此时不再读取项目里的 wrapper，直接用你指定的 Gradle 9.1.0。
7. 若 Sync 仍有其他报错：
   - **“Incompatible Gradle JVM” / “minimum compatible Gradle JVM version is 17”**：**Gradle JDK** 选 **JDK 17** 或 **21**（不要选 1.8）。
   - **“incompatible Java 25” / “maximum compatible Gradle JVM version is 24”**：**Gradle JDK** 改选 **JDK 17** 或 **21**（不要选 25）。
   - **“Unable to find method ... DependencyHandler.module”**：已用 AGP 9.0.1，需配合 Gradle 9.1+，按上面第 6 步指定本机 Gradle 9.1.0。
   - 未找到 `graphhopper-core:12.0-SNAPSHOT` → 回到第二步。
   - **“The selected directory is not a valid home for Android SDK”**：  
     **(1)** 选错层级：要选**打开后能看到 `platforms`、`build-tools`、`platform-tools`** 的那一层。  
     **(2)** 缺 `platforms`：在 **File → Settings → Android SDK → SDK Platforms** 里勾选至少一个平台（如 **Android 14.0 (API 34)**），**Apply** 安装后再选一次 SDK 路径。
   - **“Failed to find Build Tools revision 36.0.0”**：AGP 9.0.1 要求 **Build Tools 36.0.0**。到 **File → Settings → Android SDK → SDK Tools**，勾选 **Android SDK Build-Tools**，**Apply** 安装（会装 36.x）。若安装时报 **“Malformed \\uxxxx encoding”**，确保 **local.properties** 里 `sdk.dir` 用正斜杠（如 `D:/program/android-sdk`），并删掉 `build-tools` 下残留的 **36.0.0**、**36.0.0.delete** 后再装。

---

## 第四步：准备运行设备（二选一）

### 4.1 使用模拟器（推荐先用来验证）

1. 菜单 **Tools → Device Manager**（或工具栏手机图标旁的下拉）。
2. 点击 **Create Device**，选一台手机（如 Pixel 6），下一步。
3. 选系统镜像：建议 **API 30** 或 **API 34**，若无则点 **Download** 下载后继续，再 **Finish**。
4. 在 Device Manager 里点该设备旁的 **Run（三角）**，启动模拟器，等系统桌面出现。

### 4.2 使用真机

1. 手机 **设置 → 关于手机** 里连续点击 **版本号** 若干次，打开“开发者选项”。
2. 在 **设置 → 系统 → 开发者选项** 里打开 **USB 调试**。
3. 用 USB 连接电脑，手机弹出“允许 USB 调试”时点 **允许**。
4. Android Studio 顶部设备下拉中应出现你的设备名。

---

## 第五步：运行应用

1. 在 Android Studio 顶部 **设备下拉框** 中选中刚启动的模拟器或已连接的真机。
2. 点击绿色 **Run ‘app’** 按钮（或 **Run → Run ‘app’**）。
3. 等待编译、安装，应用会自动在设备上启动。

**此时会出现两种情况：**

---

## 第六步：理解运行结果（两种情形）

### 情形 1：没有准备地图数据（多数人第一次）

- **现象**：应用能正常打开，先显示“正在初始化 GraphHopper...”，然后提示 **“初始化失败: 无法复制 OSM 文件”**（或类似“文件不存在”）。
- **含义**：**应用已在安卓上成功运行**，只是 GraphHopper 需要 OSM 文件和/或 graph-cache，当前没有提供，所以初始化失败。
- **结论**：可以认为 **“能在安卓上运行”** 已验证；接下来只要按下面“情形 2”准备好数据，就能完成从初始化到算路的全流程。

### 情形 2：已准备 OSM + graph-cache（完整流程）

- **现象**：应用打开后显示“正在初始化 GraphHopper...”，随后“初始化完成”，再显示“正在算路...”，最后“算路完成”；界面下方会出现 **计算耗时**、**距离、时间、点数、高程、Path Details** 等文字结果。
- **前提**：设备上应用能访问到的路径里，已有 **OSM 文件** 和 **graph-cache**（且与 12.0-SNAPSHOT 兼容）。做法见下一节。
- **计算耗时**：结果中的「计算耗时: xxx ms」表示本次算路在设备上的实际计算时间（与路线“时间”即行程时间不同）。

---

## 第七步：把本地的 OSM + graph-cache 给安卓（三步）

你本地已有 **OSM 文件、高程数据、graph-cache**。安卓这边只需要 **OSM** 和 **graph-cache**；**高程**如果已经做进 graph-cache（服务端导入时开过 elevation），就不用再传高程数据。

---

### 三步操作（在你这台有数据的电脑上做）

**第 1 步：准备 OSM 与 graph-cache**

- **OSM**：放在 **graphhopper-master 的 `map/` 目录** 下即可（例如 `map/china-260125.osm.pbf`）。**只需保留这一份**，无需再复制到项目根目录。
- **graph-cache**：在 graphhopper-master 根目录下的 **graph-cache** 文件夹（与本地 /route 使用同一份、同一 GraphHopper 版本）。

**第 2 步：用脚本或 ADB 推到设备的“下载”目录**

先启动模拟器或连接真机并打开 USB 调试。

- **推荐：使用项目自带脚本**（从 **map/** 直接读取 OSM，不复制到根目录）：
  ```powershell
  cd D:\workspace\graphhopper-master
  ..\graphhopper-android\push-map-to-emulator.ps1
  ```
  或指定根目录：
  ```powershell
  & "D:\workspace\graphhopper-android\push-map-to-emulator.ps1" -GraphHopperRoot "D:\workspace\graphhopper-master"
  ```
  脚本会：在 `map/` 下查找 `china-260125.osm.pbf`（若无则用第一个 `*.osm.pbf`），与 **graph-cache/** 一起推送到 `/sdcard/Download/`。

- **或手动 adb**（若 OSM 已在某处且名为 `china-260125.osm.pbf`）：
  ```bash
  adb push path\to\china-260125.osm.pbf /sdcard/Download/
  adb push path\to\graph-cache /sdcard/Download/
  ```

等推送完成、无报错。

**第 3 步：在手机上打开应用**

- 若应用弹出 **存储权限**，点 **允许**。
- 打开/重新打开应用，它会从“下载”里把 `china-260125.osm.pbf` 和 `graph-cache` 拷到应用自己的目录，然后初始化、算路；看到“初始化完成”和算路结果就说明数据已给到安卓。

---

### 小结

| 你本地有的         | 要不要给安卓 | 怎么给 |
|--------------------|-------------|--------|
| OSM 文件           | 要          | 放在 graphhopper-master 的 **map/** 下，用脚本或 adb 推到 `/sdcard/Download/`（脚本会从 map/ 直接读，无需复制到根目录） |
| graph-cache 目录   | 要          | 整个文件夹推到 `/sdcard/Download/graph-cache` |
| 高程数据 (srtm 等) | 一般不用    | 若导入时已带高程，graph-cache 里已有，安卓不用再传 |

应用会从 **“下载”** 自动复制到自己的目录，无需手动拷到 `/data/...`。

### 7.1.1 config.yml 与安卓代码要一致

本地的 **config.yml** 和 **graph-cache** 严格对应（graph-cache 是用该 config 导入生成的）。  
安卓端在 `GraphHopperManager.java` 里写死了 **encoded_values**、**profiles**（universal_vehicle、universal_soldier）和 **elevation**，必须与 graphhopper-master 的 **config.yml** 一致，否则加载或算路会报错。

- **encoded_values / profiles**：与 config 的 `graph.encoded_values`、`profiles`（名称、speed/priority、turn_costs 等）一致；修改 config 并重新生成 graph-cache 后，需同步改 `setEncodedValuesString(...)` 和 `setProfiles(...)`。
- **elevation**：若 config 中启用了 `graph.elevation.provider: srtm`（或其它非 noop），安卓端需调用 **`setElevation(true)`**，否则会报 “Configured dimension elevation=false is not equal to dimension of loaded graph elevation=true”。

### 7.2 使用小地图放在 assets（仅做最小验证）

若只想验证“有 OSM 时能否复制并参与流程”（仍需要 graph-cache 才能算路）：

1. 在 `app/src/main/` 下建目录 `assets/map/`。
2. 把一个小区域 OSM 文件重命名为 `china-260125.osm.pbf` 放进 `assets/map/`。

**注意**：当前应用**不会**在手机上从 OSM 导入生成 graph-cache（会 OOM），无 graph-cache 时会直接提示“未找到 graph-cache...不可在手机上从 OSM 导入”。完整算路必须在电脑上生成 graph-cache 后按第七步推送。

---

## 第八步：故障排查速查

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| **安装失败：Requested internal only, but not enough space** | 模拟器/设备**内部存储已满**（OSM + graph-cache 复制到应用目录后约 3.5GB） | **法一**：卸载应用、删除「下载」里的 OSM 和 `graph-cache` 再重装。**法二**：Device Manager 里 **Wipe Data** 该模拟器。**法三**：新建模拟器时把 **Internal Storage** 调大（如 4096 MB+） |
| **初始化异常：NoSuchMethodError VarHandle.withInvokeExactBehavior()** | graphhopper-core 使用 Java 19 的 VarHandle API，Android 的 core-oj 未提供 | 在 **graphhopper-master** 已改 `core/.../RAMDataAccess.java`，去掉 `.withInvokeExactBehavior()`。用 **JDK 21** 重新 `mvn install -DskipTests`，再 Android **Sync + Run** |
| **初始化异常：NoClassDefFoundError javax/lang/model/SourceVersion** | JDK 编译器 API 在 Android 上不存在 | 已在 **graphhopper-master** 用 `JavaKeywords` 替代 `SourceVersion.isKeyword()`。重新 `mvn install -DskipTests` 再 **Sync + Run** |
| **初始化失败：Could not create weighting / Cannot compile expression: can't load this type of class file** | **Janino** 在运行时编译 CustomModel，Android 无法加载动态生成的类 | 已在 **graphhopper-master** 的 core 中：Android 上统一使用 **InterpretedWeightingHelper** + **CustomModelInterpreter**（预解析后解释执行），支持**任意** custom_model，不再依赖 Janino。请重新 `mvn install -DskipTests` 再 **Sync + Run** |
| **初始化失败：Configured dimension elevation=false is not equal to ... elevation=true** | graph-cache 带高程，但安卓未启用 elevation | 在 `GraphHopperManager` 中若 config 启用了 `graph.elevation.provider`（如 srtm），需调用 **`setElevation(true)`**，与 7.1.1 一致 |
| **加载图时 OOM（RAMDataAccess.loadExisting）** | 默认用 RAM 加载整图，大图超过堆限制 | 已改为用 **MMAP** 加载：`setDataAccessDefaultType(DAType.MMAP)` + `setAllowWrites(false)`，图按需映射不占满堆。若仍 OOM，可换更小区域 graph-cache 或增大模拟器 RAM / largeHeap |
| 启动/编译报 **“类文件具有错误的版本 69.0, 应为 65.0”** | GraphHopper 用 JDK 25 编译；Android 用 Java 21 | 用 **JDK 21 或 17** 重新构建 GraphHopper 并 `mvn install -DskipTests`，再 Android **Sync** |
| **mergeExtDexDebug** 报 **MethodHandle only supported with min-api 26** | graphhopper-core 使用 MethodHandle（API 26+） | 本项目已 **minSdk 26**，勿改小 |
| Gradle Sync 失败，找不到 graphhopper-core | 12.0-SNAPSHOT 不在 Maven Central | 按第二步把 core 装到本机 Maven/Gradle 并确认 `mavenLocal()` |
| 应用安装后打开即闪退 | 缺少资源或依赖冲突 | 确认 layout/strings 存在；看 **Logcat** 异常栈。只看本应用日志：`adb logcat --pid=$(adb shell pidof -s com.example.graphhopper)` 或 Android Studio Logcat 选「仅所选应用」 |
| 一直“正在初始化...” / 无 graph-cache 时 OOM | 无 graph-cache 时禁止从 OSM 导入（会 OOM） | 必须先推送 **graph-cache** 到 `/sdcard/Download/` 并授权，应用只做**加载**。无 graph-cache 时会直接提示错误 |
| **算路卡在“正在算路...”很久 / 超时** | **ch.disable=true** 或 CustomModel 导致无法用 CH，大图全图搜索 + 解释执行每条边，非常慢 | 示例已改为**短距离** + **90 秒超时**。长距离时尽量 **ch.disable=false** 并使用做过 CH 预处理的图；若必须用 CustomModel 且无法用 CH，可接受较慢或分段算路。结果中的「计算耗时」可观察实际算路时间 |
| 提示“无法复制 OSM 文件” | 未提供 OSM，且 assets 里也没有 | 属正常；按第七步准备 OSM + graph-cache |
| 算路报错或结果异常 | encoded_values / profiles / elevation 与 graph-cache 不一致 | 让 `GraphHopperManager` 与生成该 graph-cache 的 **config.yml** 一致（见 7.1.1） |

### 8.1 算路性能说明（为何 Android 比 PC 慢）

- **PC**：CustomModel 用 **Janino** 编译成字节码执行，且常用 **CH**（Contraction Hierarchies）加速，算路很快。
- **Android**：无法使用 Janino（ART 不支持运行时加载动态类），改用 **预解析 + 解释执行**（条件/取值在初始化时解析一次，算路时只做取值与运算），并缓存 EncodedValue，已明显快于“每条边都正则解析”的旧实现；但相对编译执行仍更耗 CPU。若再配合 **ch.disable=true** 或 CustomModel 导致无法用 CH，大图会做全图/大范围搜索，耗时会很长。
- **建议**：长距离算路尽量使用 **ch.disable=false** 且图做过 CH 预处理；短距离或测试可用示例中的短距离 + 90 秒超时，结果中的「计算耗时」便于对比优化效果。

### 8.2 只看本应用日志（Logcat）

- **命令行**：`adb logcat --pid=$(adb shell pidof -s com.example.graphhopper)`（仅该进程）。
- **Android Studio**：Logcat 窗口顶部选「仅所选应用」并选中 `com.example.graphhopper`。

---

## 第九步：小结：如何算“能在安卓上运行”

1. **能编译、能安装、能打开应用** → 已算“能在安卓上运行”。
2. **打开后因没有 OSM/graph-cache 而初始化失败** → 属预期，按第七步准备数据即可。
3. **准备好 OSM + graph-cache 并推送后，能“初始化完成” → “正在算路...” → “算路完成”，并看到计算耗时、距离、时间、点数、Path Details 等** → 离线路径规划在安卓上的完整流程已跑通。

按本教程从第一步做到第五步即可验证**能否在安卓上运行**；要看到算路结果，按第七步准备并推送数据。算路使用预解析后的 CustomModel 解释执行，支持任意 custom_model；结果中的「计算耗时」便于观察性能。
