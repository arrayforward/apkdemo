# GoldieSettings Android — 开发方案文档

> 复刻目标：`D:\vit\apkdemo\examples\goldieos\apps\settings`（goldieos 嵌入式设置 App）
> 输出工程：`D:\vit\apkdemo\GoldieSettingsAndroid`
> 目标设备：华为 NOH-AN00 (Android 12, arm64-v8a, 1152×2376, 560 dpi)
> Android Studio：2025.3.2 + AGP 8.x + **Java 17（仅 Java，无 Kotlin）** + minSdk 24 / targetSdk 34
> 调试环境：adb 1.0.41（已就绪）

---

## 1. 复刻范围

| 维度 | 嵌入式原版 | Android 复刻版 | 说明 |
|---|---|---|---|
| 屏幕 | 320×240 RGB565 LCD | 任意手机分辨率 | 完全按手机重排（用户已确认） |
| WS 协议 | `convai.v1` 子协议 | **完全实现** | 1:1 复刻 `cloud_gateway` 协议 |
| 信封 JSON | `{type,seq,ts,body}` | 完全一致 | 字符串/数字值与原版一一对应 |
| 音频格式 | 8 kHz mono G.711A | **完全实现** | 16 kHz PCM mic → downsample → G.711A → WS；下行反之 |
| 引擎事件 | `CONVAI_STATUS_*` | 完全一致 | 6 状态枚举保留 |
| 会话事件 | `CONVAI_EV_*` | 完全一致 | 4 事件码 |
| 语音功能 | 录音 + 播放 | **完整实现** | AudioRecord 16k → 重采样 8k → G.711A；AudioTrack 播放 |
| 状态机 | sleep/silence/speak | 完全一致 | 5 种 emotion × 3 种 play_type |
| UI 控件 | 16 个 FrameView + 50+ 子控件 | 改用 Android Material | 保留相同语义、配色、文本 |
| 资源 | RGB565 raw bytes | 转 PNG | 用 Python 脚本批量转换（见 §6） |
| 资源大小 | 320×240 原始 | 多套 `*hdpi/*xhdpi/*xxhdpi` | 默认 3× 资源 + 屏幕密度适配 |

---

## 2. WebSocket 协议规范（与原版一致）

### 2.1 握手
- URL: `ws://<host>:<port>/`（默认端口 9000，**可在 App 内配置**）
- 子协议：`Sec-WebSocket-Protocol: convai.v1`
- 协议版本：RFC 6455（13）
- 客户端必须对发送帧做 mask，服务器发送帧无 mask

### 2.2 JSON 信封（TEXT 帧）

```json
{
  "type": "<MsgType>",
  "seq":  <uint32>,    // 方向内单调递增
  "ts":   <uint64>,    // 毫秒 unix 时间戳
  "body": { ... }      // 类型相关
}
```

| type 字符串 | 方向 | body 字段 | 备注 |
|---|---|---|---|
| `hello` | C→S | `product_id, product_key, product_secret, device_name, audio_codec(=1), sample_rate(=8000)` | convai_create 时构造 |
| `hello_ack` | S→C | `session_id, server_time, audio_config{frame_ms=20, codec="g711a"}` | |
| `hello_err` | S→C | `code, message` | 鉴权失败 |
| `bye` | C→S/S→C | - | |
| `ping`/`pong` | 双向 | - | |
| `status` | S→C | `status: idle\|listening\|thinking\|answering\|interrupted\|answer_finished` | |
| `event` | S→C | `event: connected\|disconnected\|failed\|updated, details` | |
| `text` | S→C | `text: "..."` | 完整一句话 |
| `text_delta` | S→C | `text: "..."` | 流式片段 |
| `config_update` | C→S | 完整 config JSON | convai_update 内容 |
| `config_update_ack` | S→C | `result, applied_at` | |
| `config_update_err` | S→C | `code, message` | |
| `function_call` | S→C | `type="response.function_call_arguments.done", calls:[{call_id, name, arguments}]` | emotion 工具 |
| `function_call_output` | C→S | `items:[{type:"function_call_output", call_id, output:"{\"result\":\"success\"}"}]` | 必须在收到 function_call 1s 内回 |
| `error` | S→C | `code, message` | |
| `ack` | 双向 | - | |

### 2.3 二进制音频帧（BINARY 帧，13 字节头）

```
| AudioOp (1B) | Sequence (4B BE) | Timestamp (8B BE) | PCM data (G.711A) |
```

| AudioOp | 值 | 含义 |
|---|---|---|
| Frame | `0x10` | 一帧 20ms G.711A 音频 |
| Start | `0x11` | VAD 开始，结束本轮 |
| End | `0x12` | VAD 结束，触发 ASR→LLM→TTS |
| Cancel | `0x13` | 打断：清空已缓冲音频 |

### 2.4 关闭码

| 码 | 含义 |
|---|---|
| 1000 | 正常 |
| 1001 | 离开 |
| 1002 | 协议错误 |
| 1011 | 服务器内部错误 |
| 4401 | 鉴权失败 |
| 4404 | 会话未找到 |
| 4429 | 限流 |

### 2.5 一次完整对话的消息流

```
C─►S  hello
S─►C  hello_ack
S─►C  event: connected
S─►C  status: listening
[持续上行]
C─►S  audio 0x10 (20ms 帧×N)
[用户说完]
C─►S  audio 0x12 (end)
S─►C  status: thinking
S─►C  text: "你好"
S─►C  status: answering
[下行二进制音频]
S─►C  audio 0x11
S─►C  audio 0x10 (帧×N)
S─►C  audio 0x12
S─►C  status: answer_finished → listening
[若服务器调用 emotion 工具]
S─►C  function_call: {calls:[{name:"emotion", args:{emotion:"happy"}}]}
C─►S  function_call_output: {items:[{call_id, output:"{\"result\":\"success\"}"}]}
```

---

## 3. 音频编解码规格

### 3.1 上行（mic → 服务器）
1. `AudioRecord` 采样 16 000 Hz, 单声道, 16-bit PCM
2. 每 20 ms 一帧 = 320 字节 PCM
3. 重采样到 8 000 Hz（线性插值）= 160 字节 PCM/帧
4. PCM16 → G.711A（ITU-T G.711 A-law，log 压缩）= 160 字节 A-law/帧
5. 13B 头部 + 160B 数据 → WS BINARY 帧 → 服务器

### 3.2 下行（服务器 → speaker）
1. WS BINARY 帧 → 13B 头 + G.711A 数据
2. G.711A → PCM16（ITU-T G.711 A-law expand）= 160 字节 PCM
3. `AudioTrack` 流式播放，buffer 8 kHz mono 16-bit

### 3.3 编解码实现
- 文件：`convai/codec/G711A.kt`
- 算法：ITU-T G.711 标准的 A-law 编码/解码表（256 项 × 2 字节）

---

## 4. convai SDK API → Android 映射

| 原 C API | Android Kotlin 类 | 角色 |
|---|---|---|
| `convai_create` | `ConvaiEngine.create(configJson, handler)` | 分配实例 |
| `convai_start` | `engine.start(agentId, params)` | 建立 WS + 发 hello |
| `convai_stop` | `engine.stop()` | 发 bye + 关闭 WS |
| `convai_update` | `engine.update(params)` | 发 config_update |
| `convai_send_audio` | `engine.sendAudio(g711aBytes)` | 发二进制 audio 0x10 |
| `convai_send_message` | `engine.sendMessage(json)` | 发文本消息（function_call_output） |
| `convai_destroy` | `engine.destroy()` | 释放 |
| `on_convai_event` | `EventListener.onEvent(EventCode, details)` | 4 种事件 |
| `on_convai_conversation_status` | `EventListener.onStatus(Status)` | 6 种状态 |
| `on_convai_audio_data` | `EventListener.onAudioData(pcm)` | G.711A → PCM |
| `on_convai_message_data` | `EventListener.onMessage(json)` | 文本消息 |

类层次：

```
ConvaiClient (WebSocket 收发, RFC 6455 mask/unmask)
   ↓
ConvaiEngine (协议编码: hello/config_update/function_call_output)
   ↓
ConvaiBridge (状态机: audio record thread + playback thread)
   ↓
UiState (提供给 Activity/ViewModel 的可观察状态)
```

---

## 5. Android UI 屏幕清单

按手机分辨率重排（Material Components + ConstraintLayout），保留原版语义和配色。

| # | 屏幕 | Activity | 主要控件 | 原版 FrameView |
|---|---|---|---|---|
| 1 | 主屏 | `MainActivity` | Toolbar "系统设置" + 4 个大卡片按钮 | `FrameView_0` |
| 2 | AI 设置 | `AiSettingsActivity` | 左：形象/音色/个性/关系/APIKey；右：头像 + 状态指示灯 + 4 按钮 + "对话" | `FrameView_cloud` |
| 3 | 形象/音色/个性/关系 子设置 | `AiConfigListActivity` | 标题 + 列表 + 选中 | `FrameView_config_wm` |
| 4 | 对话页 | `TalkActivity` | 头像眼睛动画 + 领结 + 状态文字 | `FrameView_talk` |
| 5 | WiFi 设置 | `WifiSettingsActivity` | 标题 + 开关 + 列表 | `FrameView_wifi` |
| 6 | 密码框 | `WifiPasswordDialog` | 输入框 + 确定/取消 | `FrameView_wifipasswd` |
| 7 | 音量 | `VolumeActivity` | 标题 + 进度条 + 数值 | `FrameView_volume` |
| 8 | 星闪 | `SleSettingsActivity` | 标题 + 模式 + 设备列表 | `FrameView_sle`（**用占位实现**，因为原版是嵌入式近场协议） |
| 9 | 服务器配置 | `ServerConfigActivity` | WS URL / agent_id / product_id 等输入 | 自加 |

### 5.1 配色映射

| 原 RGB565 16-bit | ARGB 等价 | 用途 |
|---|---|---|
| `0xFFFF` (白) | `#FFFFFF` | 背景 |
| `0x0000` (黑) | `#000000` | 顶栏 |
| `0x3CE7` (浅蓝) | `#C6E7F3` | 卡片底色 |
| `0x3F03` (深绿) | `#3F8F03` | 按钮/确认 |
| `0x4C6B` (灰) | `#4C6B7F` | 取消 |
| `0x9AD6` (浅紫) | `#9AD6E7` | 返回 |
| `0xBAD6` (米色) | `#BAD6E7` | 返回 |
| `0xFD87` (橙) | `#FD873F` | WiFi 确定 |
| `0x55AD` (灰绿) | `#55AD7F` | 取消2 |
| `0x1082` (深蓝) | `#1082A4` | 状态: 空闲 |
| `0x0410` (深绿) | `#041080` | 状态: 倾听 |
| `0xFC00` (黄) | `#FCFC00` | 状态: 思考 |
| `0x07E0` (绿) | `#07E007` | 状态: 回答 |
| `0xF800` (红) | `#F80000` | 状态: 打断 |

---

## 6. 资源提取与转换

### 6.1 工具脚本
- `tools/img2png.py`：RGB565 raw bytes → PNG（用 Pillow）
- `tools/extract_assets.sh`：从 `D:\vit\apkdemo\examples\goldieos\apps\settings\assets` 提取所有 .h 文件

### 6.2 资源清单
**来自 `settings/assets/`：**
- 头像：`rgb16_avatar_female_152_136`, `rgb16_avatar_male_152_136`
- 主页图标 40×40：`rgb16_cloud_icon`, `rgb16_audio_icon`, `rgb16_sle_icon`, `rgb16_wifi_icon1`
- 主页图标 24×24：`rgb16_voice`, `rgb16_preson`, `rgb16_relat`, `rgb16_apikey`, `rgb16_avat`
- 开关：`rgb16_switch_pressed_56_24/25/26`, `rgb16_switch1_released_56_24/25/26`, `rgb16_switch2_pressed_56_26`, `rgb16_switch6_released_56_26`
- 返回：`rgb16_back3_48_24`
- +/-：`rgb16_add1_64_41`, `rgb16_minus_64_43`
- 选中：`rgb16_selected_32_32`

**来自 `AItalk/assets/`（对话动画）：**
- 眼睛 88×85：`rgb16_eye`, `rgb16_closeeye_l/r`, `rgb16_closeeye_r1/r2/r3`, `rgb16_half_l/r`, `rgb16_laugh_l/r`, `_new` 后缀为女性版本
- 愤怒：`rgb16_angry_{male,female}_{l,r1,r2}_88_85`
- 怀疑：`rgb16_doubt_{male,female}_{l,r1,r2}_88_85`
- 伤心：`rgb16_sad_{male,female}_{l,r}_88_85`
- 领结：`rgb16_bow_56_53`（女），`rgb16_bowtie_56_53`（男）

总共约 50+ PNG，转换后存到 `app/src/main/assets/convai/img/`，运行时用 `BitmapFactory.decodeStream(assets.open(...))` 加载。

---

## 7. 工程结构

```
D:\vit\apkdemo\GoldieSettingsAndroid\
├── settings.gradle.kts
├── build.gradle.kts                        # root
├── gradle.properties
├── gradle/wrapper/...
├── gradlew.bat
├── docs/
│   ├── DEV_PLAN.md                         ← 本文档
│   ├── PROTOCOL.md                         ← WS 协议详细规范
│   ├── UI_MAPPING.md                       ← 原版 → Android 控件映射
│   └── ASSETS.md                           ← 资源清单
├── tools/
│   ├── img2png.py                          ← RGB565 → PNG 转换
│   └── extract_assets.bat
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/anomaly/goldiesettings/
│   │   │   ├── App.java
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.java
│   │   │   │   ├── AiSettingsActivity.java
│   │   │   │   ├── AiConfigListActivity.java
│   │   │   │   ├── TalkActivity.java
│   │   │   │   ├── WifiSettingsActivity.java
│   │   │   │   ├── VolumeActivity.java
│   │   │   │   ├── SleSettingsActivity.java
│   │   │   │   ├── ServerConfigActivity.java
│   │   │   │   ├── view/TalkAvatarView.java  ← 眼睛动画
│   │   │   │   └── view/RoundRectButton.java
│   │   │   ├── convai/
│   │   │   │   ├── ConvaiEngine.java         ← C API 映射
│   │   │   │   ├── ConvaiBridge.java         ← 状态机/录音/播放
│   │   │   │   ├── ConvaiClient.java         ← WS 收发
│   │   │   │   ├── ConvaiTypes.java          ← 枚举
│   │   │   │   ├── codec/G711A.java          ← A-law 编解码
│   │   │   │   ├── audio/AudioCapture.java   ← AudioRecord
│   │   │   │   ├── audio/AudioPlayer.java    ← AudioTrack
│   │   │   │   ├── audio/Resampler.java      ← 16k→8k
│   │   │   │   └── proto/
│   │   │   │       ├── Envelope.java
│   │   │   │       ├── MsgType.java
│   │   │   │       ├── AudioOp.java
│   │   │   │       ├── WsFrames.java          ← RFC 6455 客户端编/解码
│   │   │   │       └── Hello.java
│   │   │   ├── model/
│   │   │   │   ├── Settings.java             ← 形象/音色/个性/关系/api_key
│   │   │   │   └── VoicePresets.java
│   │   │   └── util/Logger.java
│   │   ├── res/
│   │   │   ├── layout/                     ← 各 Activity/Item
│   │   │   ├── drawable/                   ← 背景 shape（圆角矩形）
│   │   │   ├── values/{strings,colors,themes}.xml
│   │   │   └── ...
│   │   └── assets/convai/img/              ← 转换后的 PNG
│   └── proguard-rules.pro
└── build/                                  # gradle 产物
```

---

## 8. 关键技术决策

| 决策 | 选型 | 原因 |
|---|---|---|
| 语言 | **Java 17**（仅 Java，无 Kotlin） | 用户指定 |
| UI | Material 3 + ViewBinding（`com.google.android.material:material`） | 不上 Compose 以减小 APK、加快编译 |
| WS 库 | **OkHttp 4.12**（`okhttp3.WebSocket`） | OkHttp 内置 WebSocket：自动 mask、分片、ping/pong、ping 间隔、二进制帧；与本项目"完整复刻协议语义"无冲突（我们仍要完整实现 JSON 信封、AudioOp 13 字节头、hello/config_update/function_call 状态机），只是不再手写 mask/unmask |
| 依赖管理 | 仅 `com.squareup.okhttp3:okhttp:4.12.0` + Material | 不引入 RxJava/Coroutines/Gson 等，保持精简 |
| 录音 | `AudioRecord` 16 kHz mono | 标准 Android API |
| 播放 | `AudioTrack` 8 kHz mono stream | 同上 |
| 状态 | `LiveData` 暴露给 UI | Android 原生，避免 Kotlin 协程 |
| 配置持久化 | `SharedPreferences`（包名 `goldie_settings`） | 简单稳定 |
| 日志 | `Log.i/d/e` + tag `Convai` | adb logcat -s Convai |
| 资源加载 | `assets/convai/img/*.png` | 一次性打 APK |
| 屏幕方向 | portrait | 手机默认 |

### 为什么用 OkHttp
- WebSocket 部分（TCP + TLS + RFC 6455 握手 + frame 编码）由 OkHttp 负责（业界标准、久经考验）
- 我们仍要"完整复刻 convai 应用层协议"——即：JSON 信封、MsgType、AudioOp、function_call 流程、hello→ack 状态机、G.711A 音频编解码等全部在应用层手写
- OkHttp 仅替代 TCP+WS 帧层，**不替代**应用层 convai 协议
- 优势：免维护、自动兼容 Android 各种网络栈、ping/pong 自动

---

## 9. 调试环境

### 9.1 准备
- `adb devices` 确认 `PQY5T20C18016458 device`
- `adb -s PQY5T20C18016458 install -r app/build/outputs/apk/debug/app-debug.apk`
- `adb -s PQY5T20C18016458 shell am start -n com.anomaly.goldiesettings/.ui.MainActivity`
- `adb -s PQY5T20C18016458 logcat -v time -s Convai:V ConvaiAudio:V ConvaiWs:V TalkAvatar:V AndroidRuntime:E`

### 9.2 测试模式
- App 内"服务器配置"页可填 `ws://<PC_IP>:9000`、product_id、agent_id
- 如果无服务器，App 仍可启动、UI 全部可用，仅 WS 部分降级
- "开始对话"前若未连接，提示"未连接"

### 9.3 调试版
- `applicationIdSuffix ".debug"`
- `versionNameSuffix "-debug"`
- `isDebuggable = true`
- 单独打 `release-unsigned.apk` 用于自测

---

## 10. 实施步骤

| 步骤 | 任务 | 验证 |
|---|---|---|
| 1 | 工程骨架 + Gradle 配置 | Android Studio 能 sync |
| 2 | RGB565 → PNG 转换脚本 | `tools/extract_assets.bat` 跑通 |
| 3 | WS 帧编解码 + ConvaiClient | 单元测试: 握手/发文本/收文本/二进制 |
| 4 | G.711A 编解码 | 单元测试: 已知样本 |
| 5 | ConvaiEngine + Bridge | hello→ack 状态机可手动触发 |
| 6 | AudioCapture + Resampler + AudioPlayer | 录 1s 存 wav、回放一致 |
| 7 | 各 Activity UI | 在手机上能点能跳 |
| 8 | TalkAvatarView 眼睛动画 | 5 状态 × 3 play_type × 男女共 ~30 sprite |
| 9 | 编译 debug APK | assembleDebug 通过 |
| 10 | 安装 + 启动 + 截屏 | adb screencap 看到主屏 |
| 11 | 真机走通 hello→ack | logcat 看到 hello_ack |
| 12 | （可选）连 cloud_gateway.exe 端到端 | 启动 PC 服务，App 端收到 status |

---

## 11. 风险与缓解

| 风险 | 缓解 |
|---|---|
| RGB565 资源文件几百个，转换慢 | 一次性脚本，5 分钟跑完；输出缓存到 `tools/output/` |
| 320×240 资源在手机屏上太小 | 用 `NearestNeighbor` 缩放 3× 避免模糊 |
| 嵌入式图片可能在手机渲染发糊 | 同时生成 `*@3x` 资源 + 自动选择 |
| 录音权限被拒 | Activity 用 `ActivityResultContracts.RequestPermission` 请求；拒绝后弹设置引导 |
| WS 连接无服务器失败 | 降级：所有 UI 仍可用，仅"开始对话"提示未连接 |
| 设备 Android 12 严格模式 | 不在前台服务，UI 内有网络状态指示 |

---

## 12. 验收标准

1. APK 可在 PQY5T20C18016458 上安装、启动
2. 主屏显示 4 个设置入口（AI/音量/星闪/WiFi）
3. AI 设置页可切换形象/音色/个性/关系，配置 JSON 与原版 1:1
4. 对话页显示眼睛动画 + 状态文字
5. WS 协议与 `cloud_gateway.exe` 互通：发 hello→收到 hello_ack→进入 listening
6. 录音线程跑 20ms 帧，发送 G.711A 二进制帧
7. 播放线程正确解码服务器音频，AudioTrack 持续输出
8. function_call `emotion` 收到后 1s 内回 `function_call_output` 含 `{"result":"success"}`
9. logcat 清晰可追踪每个状态变更
10. 关闭 App 正确释放 AudioRecord/AudioTrack/WebSocket

