# GoldieOS App 开发指南

## 概述

GoldieOS 是面向 HiSilicon WS63 (RISC-V) 的嵌入式 RTOS 平台，配备 320x240 屏幕、触摸输入、GPIO 按键、SLE（星闪）无线通信、WiFi、SD 卡、电池和音频编解码器。应用使用 C++ 编写，基于 tinyui GUI 框架和面向服务的架构。

---

## 1. 项目结构

```
goldieos/
  include/
    core/           # 核心框架头文件（服务、应用管理、驱动）
    gui/            # tinyui GUI 控件头文件
    osal/           # 操作系统抽象层
    services/       # 服务专用头文件（闹钟、NTP、音频算法）
  apps/             # 所有应用代码放这里
  sdk_integration/  # AI SDK 桥接层（convai_bridge）
  platform/         # 平台抽象层
  services/         # 服务实现
```

**每个 App 目录**包含：
- `main_app.cpp` — 应用逻辑、生命周期、服务调用、业务代码
- `main_ui.h` — tinyui 工具自动生成的 UI 文件，静态声明所有控件
- `assets/` — 图片精灵（RGB565 格式的 C 头文件）

---

## 2. App 生命周期契约

每个 App **必须**定义 6 个回调函数并通过 `install_app()` 注册：

### 2.1 App_t 结构体

```cpp
typedef struct App_t {
    char app_name[32];          // 应用名称（最长 32 字符）
    void (*h_app_run)(void);    // 应用启动时调用
    void (*h_app_exit)(void);   // 应用关闭时调用
    void (*h_app_suspend)(void);// 应用进入后台时调用
    void (*h_app_resume)(void); // 应用回到前台时调用
    void (*h_touch_event)(int,int,int);  // (按压状态, x坐标, y坐标)
    void (*h_keyboard_event)(int,int);   // (按压状态, 按键值)
    uint16_t* icon;             // 56x56 RGB565 启动器图标（可为 NULL）
    int app_status;             // 框架管理，初始化为 0
} App_t;
```

### 2.2 注册模板（必须照此格式）

```cpp
// 步骤一：将生命周期函数定义为 static
static void goldie_app_run(void) { /* ... */ }
static void goldie_app_exit(void) { /* ... */ }
static void goldie_app_suspend(void) { /* ... */ }
static void goldie_app_resume(void) { /* ... */ }
static void goldie_touch_event(int pressure, int x, int y) {
    if (Window_main) Window_main->handleEvent(pressure, x, y);
}
static void goldie_keyboard_event(int pressure, int key);

// 步骤二：定义 App_t 实例
static App_t my_app = {
    "我的应用",                // app_name（应用名称）
    goldie_app_run,            // h_app_run
    goldie_app_exit,           // h_app_exit（必须填写）
    goldie_app_suspend,       // h_app_suspend（必须填写）
    goldie_app_resume,        // h_app_resume（必须填写）
    goldie_touch_event,       // h_touch_event（必须填写）
    goldie_keyboard_event,   // h_keyboard_event（必须填写）
    (uint16_t*)my_icon_56_56, // icon（应用图标，可为 NULL）
};

// 步骤三：注册入口函数 + 自动初始化宏
static void my_app_entry(void) {
    install_app(&my_app);
}
GOLDIE_INIT_CALL_(my_app_entry);
```

### 2.3 生命周期实现模板

```cpp
static void goldie_app_run(void) {
    // 1. 获取所需服务
    // 2. 初始化 UI（调用 main_ui_init()）
    // 3. 设置回调函数
    // 4. 按需创建线程
    // 5. 设置运行标志
}

static void goldie_app_exit(void) {
    // 1. 将 running_flag 置 0，停止所有线程
    // 2. 停止音频播放/录音
    // 3. 关闭文件句柄
    // 4. 等待线程退出（检查 thread_exit_flag）
    // 5. 销毁线程
    // 6. 注销回调函数
    // 7. 调用 window_exit() 清理所有 UI 控件
    // 8. 恢复之前暂停的服务（如 AualgoService）
}

static void goldie_app_suspend(void) {
    // 1. 停止动画 / 停止标志
    // 2. 调用 window_suspend() 隐藏窗口
}

static void goldie_app_resume(void) {
    // 1. 调用 window_resume() 显示窗口
    // 2. 重启动画 / 刷新 UI
}

static void goldie_keyboard_event(int pressure, int key) {
    if ((key == SYSTEM_KEY_VALUE_BACK) && (pressure == 1)) {
        goldie_exit_app(&my_app);
    }
    // WAKEUP 键 (301) 可用于应用自定义快捷操作
    if ((key == SYSTEM_KEY_VALUE_WAKEUP) && (pressure == 1)) {
        // 处理唤醒操作
    }
}
```

---

## 3. main_ui.h —— 自动生成的 UI 文件

`main_ui.h` 由 tinyui 界面设计工具生成，**不应手动修改**控件结构。它提供以下内容：

### 3.1 main_ui.h 的内容

```cpp
// 包含所有控件头文件、图片资源、边界遮罩头文件
// 以 static std::shared_ptr<控件类型> 声明所有控件
// 提供三个生命周期函数：

static void main_ui_init();    // 创建所有控件，建立层级关系，刷新显示
static void window_exit();     // 清理所有控件（reset 所有 shared_ptr）
static void window_suspend();  // Window_main->setVisible(false)
static void window_resume();   // Window_main->setVisible(true) + flush
```

### 3.2 手动编写 main_ui.h

当没有 tinyui 工具时，需手动按以下模式编写 `main_ui.h`：

```cpp
#ifndef INCLUDE_MAIN_UI_H
#define INCLUDE_MAIN_UI_H
#include <memory>
#include "goldie_display.h"
#include "window.h"
#include "frame_view.h"
#include "label_view.h"
#include "button_view.h"
// ... 其他控件头文件和资源头文件 ...

// 声明全局控件变量
static std::shared_ptr<LabelView> LabelView_title;
static std::shared_ptr<ButtonView> Button_ok;
static std::shared_ptr<FrameView> FrameView_main;
static std::shared_ptr<Window> Window_main;

static void main_ui_init() {
    // 用 std::make_shared<控件类型>(宽, 高) 创建控件
    // Window 使用: std::make_shared<Window>(APP_WINDOW_START_X, APP_WINDOW_START_Y, 宽, 高)
    控件 = std::make_shared<控件类型>(w, h);
    控件->setColor(0xFFFF);       // 背景色（RGB565 格式）
    控件->setTextColor(0x0000);   // 文字颜色
    控件->setVisible(true);
    // 可选：setImageBuffer, setBoundaryBuffer, setMaskBuffer

    // 建立层级关系：父控件->addView(子控件, x, y)
    FrameView_main->addView(LabelView_title, 10, 20);
    Window_main->addView(FrameView_main, 0, 0);

    // 刷新到屏幕
    Window_main->flush(0, 0, APP_WINDOW_WIDTH, APP_WINDOW_HEIGHT);
}

static void window_exit() {
    Window_main->setVisible(false);
    Window_main->removeAllViews();
    FrameView_main->removeAllChilds();
    // Reset 全部 shared_ptr
    控件.reset();
    Window_main.reset();
}

static void window_suspend() {
    Window_main->setVisible(false);
}

static void window_resume() {
    Window_main->setVisible(true);
    Window_main->flush(0, 0, APP_WINDOW_WIDTH, APP_WINDOW_HEIGHT);
}
#endif
```

---

## 4. 系统服务 API 参考

服务的获取方式：
- `wait_service(SERVICE_INDEX)` — 阻塞等待，直到服务就绪
- `get_service(SERVICE_INDEX)` — 非阻塞，若未就绪则返回 NULL

### 4.1 服务索引枚举

```cpp
enum SERVICE_INDEX {
    AUDIO_SERVICE_INDEX     = 0,   // 音频服务
    EVENT_SERVICE_INDEX     = 1,   // 事件/消息服务
    WIFI_SERVICE_INDEX      = 2,   // WiFi 服务
    SDCARD_SERVICE_INDEX    = 3,   // SD 卡服务
    CLOUD_SERVICE_INDEX     = 4,   // 已废弃，改用 convai_bridge
    AUALGO_SERVICE_INDEX    = 5,   // 音频算法服务（唤醒词检测）
    SLE_SDP_SERVICE_INDEX   = 6,   // 星闪无线服务
    NTP_SERVICE_INDEX       = 7,   // NTP 时间同步
    ALARM_SERVICE_INDEX     = 8,   // 闹钟服务
    SLE_WTP_SERVICE_INDEX   = 9,   // 星闪对讲音频传输服务
    MAX_SERVICE_INDEX,
};

void* wait_service(int service_index);  // 阻塞等待服务就绪
void* get_service(int service_index);   // 非阻塞获取，可能返回 NULL
```

### 4.2 音频服务 (AudioService)

```cpp
AudioService *audio = (AudioService*)wait_service(AUDIO_SERVICE_INDEX);

// 音频 I/O（立体声 16-bit PCM）
audio->audio_input_config(int flags);               // 配置音频输入
audio->audio_read(void* buffer, unsigned int len);   // 读取 PCM 数据（返回实际读取字节数）
audio->audio_output_config(int flags);               // 配置音频输出
audio->audio_write(const void* buffer, unsigned int len); // 写入 PCM 数据

// 播放控制
audio->play_start();     // 启动音频输出流
audio->play_stop();      // 停止音频输出流
audio->record_start();   // 启动音频输入流
audio->record_stop();    // 停止音频输入流

// 铃声 (ring_id: NOTIFY=0, POWERON=1, WAKEUP=2, SLEEP=3, ALARM=4)
audio->play_ring(int ring_id, int loop);  // 播放铃声（loop: 0=播放一次, 1=循环播放）
audio->stop_ring();       // 停止铃声
audio->wait_ring();       // 阻塞等待铃声播放结束

// 音量控制 (stream_type: SYSTEM=0, USER=1)
audio->set_volume(int stream_type, float volume);  // volume: 0.0 ~ 1.0
float vol = audio->get_volume(int stream_type);

// 语音活动检测 (VAD) 和算法流
int is_speech = audio->vad_detect(short* buffer, unsigned int len);
audio->algostream_start();    // 启动算法处理流
audio->algostream_stop();     // 停止算法处理流
audio->reset_playbuffer();    // 重置播放缓冲区
audio->algostream_read(void* buffer, unsigned int len);
unsigned int valid = audio->get_valid_length(int* flag);
```

**音频管道说明：**
- 录音链路：硬件 → 立体声 16-bit PCM → `audio_read()`（通常 8kHz，80ms 帧）
- 播放链路：`audio_write(PCM 数据)` → 硬件
- 常用常量：`SAMPLERATE=8000, FRAME_MS=80, BYTES_PER_FRAME=2`
- 缓冲区大小：`2 × BYTES_PER_FRAME × FRAME_MS × SAMPLERATE / 1000 = 2560 字节`

### 4.3 事件服务 (EventService)

```cpp
EventService *evt = (EventService*)wait_service(EVENT_SERVICE_INDEX);

// 广播消息（跨应用/跨服务通信）
int8_t ret = evt->register_broadcast_recv(uint16_t broadcast_id, boardcast_callback cb);
evt->send_broadcast(BroadcastMessage* msg);

// 广播 ID 定义：
//   EVENT_BROADCAST_ADDMSG (1)    - 新消息通知
//   EVENT_BROADCAST_INPUT_METHOD (2) - 输入法
//   EVENT_BROADCAST_POWER (3)     - 电池电量更新
//   EVENT_BROADCAST_TIMERUPDATE (4) - 时间更新（msg 中包含 struct tm*）
//   EVENT_BROADCAST_SHUTDOWN (5)  - 系统关闭通知

// BroadcastMessage 结构体：
typedef struct {
    uint16_t id;           // 广播 ID
    void* msg;             // 数据指针
    uint16_t msg_len;      // 数据长度
    unsigned int ext[4];   // 扩展字段
} BroadcastMessage;

// 按键事件
evt->send_keyevent(int keycode);
evt->register_keyevt_cb(int keycode, KeyEventHandler handler);

// 按键码定义：
//   EVENT_KEY_CODE_WAKEUP (1), CLOUD_SLEEP (2), CLOUD_RESTART (3),
//   CLOUD_STOP (4), START_RECORD (5), WAKEUP_AIATLK (6), TTS (7),
//   NEW_MSG (8), START_PLAY (9), FINISH_PLAY (10), PLAY (11),
//   VOLUME_DOWN (12), VOLUME_UP (13), PLAY_NEXT (14), STOP (15),
//   SDCARD_INSERT (16), SDCARD_REMOVE (17), HEADSET_INSERT (18),
//   HEADSET_REMOVE (19), SLE_WTP_EVENT (20)
```

### 4.4 WiFi 服务 (WifiService)

```cpp
WifiService *wifi = (WifiService*)wait_service(WIFI_SERVICE_INDEX);

// STA 模式（连接路由器）
int is_enabled = wifi->svr_sta_isEnabled();        // WiFi 是否已启用
int is_connected = wifi->svr_sta_isConnected();    // 是否已连接
int is_scan_done = wifi->svr_sta_isScanDone();     // 扫描是否完成
char* ssid = wifi->svr_sta_connection_name();      // 当前连接的 SSID
int count = wifi->svr_sta_get_ap_list(ApInfo_t* ap_list);  // 获取 AP 列表
wifi->svr_sta_add_account(char* ssid, char* passwd);      // 添加网络账号
wifi->svr_sta_remove_account(char* ssid);                  // 删除网络账号
wifi->svr_sta_try_connect();                               // 尝试连接

// SoftAP 模式（配网用热点）
wifi->svr_softap_config(char* ssid, char* passwd);  // 配置热点
wifi->svr_softap_get_config(WifiConfig* config);    // 获取热点配置
int is_ap = wifi->svr_softap_isEnabled();            // 热点是否启用

// 事件和回调
wifi->register_callback(WifiEventCallback cb);      // 注册事件回调
wifi->trigger_event(WifiEventType type, void* data); // 触发事件
wifi->svr_check_ap_config(char* ssid, char* passwd); // 检查 AP 配置
wifi->svr_get_netddr(char* buffer, int len);         // 获取 IP 地址
wifi->svr_get_hwddr(uint8_t* mac, int len);          // 获取 MAC 地址
wifi->svr_net_components_setup();                    // 初始化网络组件

// 回调函数签名：void callback(int event, void* data)
// 事件类型：NONE, STA_ENABLE, STA_DISABLE, STA_SCAN, STA_CONNECT,
//          STA_DISCONNECT, SOFTAP_ENABLE, SOFTAP_DISABLE, SOFTAP_CONFIG
// 状态类型：NONE, STA_ENABLED, STA_DISABLED, CONNECTED, DISCONNECTED,
//          SCANDONE, SOFTAP_ENABLED, SOFTAP_DISABLED
```

### 4.5 星闪无线服务 (SleSdpService)

```cpp
#ifdef SUPPORT_SLE
SleSdpService *sle = (SleSdpService*)wait_service(SLE_SDP_SERVICE_INDEX);

// 启用/禁用
sle->svr_enable();                      // 启用星闪
sle->svr_disable();                     // 禁用星闪
bool enabled = sle->svr_is_enabled();    // 是否已启用

// 设备信息
sle->svr_set_local_name(const char* name);        // 设置本机名称
sle->svr_get_local_name(sle_addr_t* addr);        // 获取本机名称

// 设备扫描
sle->svr_start_scan();                  // 开始扫描
sle->svr_stop_scan();                   // 停止扫描
bool scanning = sle->svr_is_scanning();  // 是否正在扫描
sle->svr_get_scan_results(sle_device_info_t* devices, uint32_t* count);

// 已配对设备
sle->svr_get_paired_devices(sle_connection_info_t* devices, uint32_t* count);
sle->svr_get_paired_and_discovered_devices(sle_device_info_t* devices, uint32_t* count);

// 连接管理
sle->svr_connect(const sle_addr_t* addr);        // 主动连接
sle->svr_disconnect(uint16_t conn_id);           // 断开连接
sle->svr_get_connections(sle_connection_info_t* conns, uint32_t* count);
bool connected = sle->svr_is_connected(const sle_addr_t* addr); // 检查是否已连接
int8_t rssi; sle->svr_get_rssi(uint16_t conn_id, &rssi);        // 获取信号强度

// 设备广播（从机模式）
sle->svr_start_announce();               // 开始广播
sle->svr_stop_announce();                // 停止广播
bool announcing = sle->svr_is_announcing(); // 是否正在广播

// 配对
int pair_state = sle->svr_get_pair_state(const sle_addr_t* addr); // 获取配对状态
int pair_code = sle->svr_s_generate_pair_code();  // 从机生成配对码

// 发送数据（原始 SDP 数据）
sle->svr_send_data(uint16_t conn_id, const uint8_t* data, uint32_t len);

// 模式管理：0=主机模式, 1=从机模式
uint8_t mode = sle->svr_get_mode();      // 获取当前模式
sle->svr_set_mode(uint8_t mode);         // 设置模式

// 事件和回调
sle->register_callback(SleEventCallback cb, int unused);          // 注册状态回调
sle->register_data_receive_callback(SleDataReceiveCallback cb, int unused); // 注册数据接收回调
sle->trigger_event(SleEventType type, void* data);                // 触发事件

// 状态类型：DISABLED, ENABLED, SCANNING, SCAN_DONE, ANNOUNCING,
//           ANNOUNCE_STOPPED, DEVICE_LIST_UPDATED,
//           CONNECTION_LIST_UPDATED, PAIRED_STATUS_UPDATED

// 事件类型：ENABLE, DISABLE, START_SCAN, STOP_SCAN, CONNECT,
//          DISCONNECT, SEND_DATA, RECEIVE_DATA, START_ANNOUNCE,
//          STOP_ANNOUNCE, PAIR, UNPAIR, SLAVE_PAIRED
#endif
```

### 4.6 星闪对讲音频服务 (SleWtpService)

```cpp
sleWtpService *wtp = (sleWtpService*)wait_service(SLE_WTP_SERVICE_INDEX);

// 通过星闪传输音频流
int ret = wtp->write_data(const uint8_t* data, uint32_t len, bool is_start, bool is_end);
// 发送音频数据：is_start 标记首个包，is_end 标记最后一个包
int ret = wtp->read_data(uint8_t* buffer, uint32_t len, uint16_t* conn_id);
// 读取接收到的音频数据，conn_id 输出来源设备
wtp->read_notify(wtp_notify_data* notify);   // 读取通知（用于铃声/振动提醒）
wtp->clear_notify();                         // 清除通知

// 对讲机音频编解码器初始化/销毁
wtp->voice_codec_init();
wtp->voice_codec_destroy();

// 数据包格式：data_head(0xFF0E) + magic_num0(0x1314) + magic_num1(0xF3F4) +
//             data_len + data_index + buffer[]
// data_index_end = 0xFFFF, data_index_start_end = 0xFFF0
```

### 4.7 SD 卡服务 (SdCardService)

```cpp
SdCardService *sd = (SdCardService*)wait_service(SDCARD_SERVICE_INDEX);

bool exists = sd->IsSdCardExists();   // 检查 SD 卡是否存在
sd->mount_disk();                      // 挂载 SD 卡
sd->unmount_disk();                    // 卸载 SD 卡
```

### 4.8 闹钟服务 (AlarmService)

```cpp
AlarmService *alarm = (AlarmService*)wait_service(ALARM_SERVICE_INDEX);
// 注：通常用 get_service() 获取，因为服务可能尚未初始化

alarm->init();                                    // 初始化闹钟服务
int index = alarm->add_alarm(AlarmInfo* info);    // 添加闹钟（返回索引）
alarm->del_alarm(int index);                      // 删除闹钟
alarm->update_alarm(int index, AlarmInfo* info);  // 更新闹钟
int count = alarm->get_alarm_list(AlarmInfo* list, int max_count); // 获取闹钟列表
alarm->cancel_alarm();                            // 取消正在响的闹钟
int active_idx = alarm->get_actived_alarm();      // 获取当前正在响的闹钟索引

// AlarmInfo 结构体：
typedef struct {
    bool enabled;          // 是否启用
    char m_hour, m_min;    // 小时、分钟
    bool weekdays[7];      // 重复星期（索引0=周一 ... 6=周日）
    char ring_index;       // 铃声索引
} AlarmInfo;
```

### 4.9 音频算法服务 (AualgoService)

```cpp
AualgoService *algo = (AualgoService*)get_service(AUALGO_SERVICE_INDEX);

// 在进行音频操作（录音、播放）前必须先暂停
algo->puase();   // 暂停唤醒词检测
algo->run();     // 恢复唤醒词检测

// 重要：如果暂停了，退出应用时务必恢复！
```

### 4.10 NTP 时间服务 (NTPService)

```cpp
NTPService *ntp = (NTPService*)wait_service(NTP_SERVICE_INDEX);

ntp->sync_time();               // 从网络同步时间
ntp->get_time(struct tm*);      // 获取当前时间
```

---

## 5. 驱动访问 (Driver Access)

```cpp
enum DRIVER_INDEX {
    I2C_DRV_INDEX=0, DISP_DRV_INDEX=1, FATFS_DISK_DRV_INDEX=2, I2S_DRV_INDEX=3,
    WLAN_DRV_INDEX=4, TOUCH_DRV_INDEX=5, KEYBOARD_DRV_INDEX=6, SPI1_DRV_INDEX=7,
    GPIO_EXT_DRV_INDEX=8, HW_TOUCH_DRV_INDEX=9, HW_KEYBOARD_DRV_INDEX=10,
    BATTERY_DRV_INDEX=11, SLE_DRV_INDEX=12, RTC_DRV_INDEX=13,
    PA_DRV_INDEX=14, FLASH_DRV_INDEX=15, MAX_DRV_INDEX
};

// 阻塞等待
BatDrv* bat = (BatDrv*)wait_drv(BATTERY_DRV_INDEX);
// 带超时的等待
void* drv = wait_drv_timeout_ms(DRV_INDEX, int timeout_ms);
// 非阻塞获取
void* drv = get_drv(DRV_INDEX);

// 电池驱动使用示例：
BatDrv* bat = (BatDrv*)wait_drv(BATTERY_DRV_INDEX);
int soc = bat->read_power();       // 电池电量百分比 0-100
int charging = bat->is_charging(); // 是否充电中，1=充电
```

---

## 6. OSAL（操作系统抽象层）

```cpp
#include "goldie_osal.h"

// 线程
void* thread = goldie_thread_create(
    int (*handler)(void* data),  // 线程入口函数
    void* data,                  // 传递给线程的参数
    "thread_name",               // 线程名称（调试用）
    unsigned int stack_size      // 栈大小（通常 0x1000 = 4KB）
);
goldie_thread_set_priority(void* thread, unsigned int priority); // 优先级 0-31，越小越高
goldie_thread_destroy(void* thread);     // 销毁线程
goldie_thread_lock();                    // 获取调度器锁
goldie_thread_unlock();                  // 释放调度器锁

// C++ 线程封装（推荐在 C++ 应用中使用）
#include "goldie_thread.h"
// 构造：Goldie_Thread(处理函数, 数据指针, 栈大小)
Goldie_Thread* t = new Goldie_Thread(play_task, NULL, 0x1000);
delete t;  // 析构时自动停止并清理

// 互斥锁
goldie_mutex mutex;
goldie_mutex_init(&mutex);      // 初始化
goldie_mutex_lock(&mutex);      // 加锁
goldie_mutex_unlock(&mutex);    // 解锁
goldie_mutex_destroy(&mutex);   // 销毁

// 信号量
goldie_sem sem;
goldie_sem_init(&sem);          // 初始化
goldie_sem_wait(&sem);          // 等待（P 操作）
goldie_sem_post(&sem);          // 释放（V 操作）
goldie_sem_destroy(&sem);       // 销毁

// 睡眠
goldie_msleep(int milliseconds);  // 毫秒级睡眠

// 内存管理
void* ptr = goldie_malloc(unsigned long size);  // 分配内存
goldie_free(void* addr);                         // 释放内存

// 时间
goldie_timeval tv;
goldie_gettimeofday(&tv);  // 获取时间戳，tv.tv_sec = 秒, tv.tv_usec = 微秒

// 系统按键值
#define SYSTEM_KEY_VALUE_BACK   300   // 返回键
#define SYSTEM_KEY_VALUE_WAKEUP 301   // 唤醒/功能键
```

---

## 7. 屏幕与布局常量

```cpp
#include "goldie_display.h"

#define DISPLAY_WIDTH   320      // 屏幕宽度
#define DISPLAY_HEIGHT  240      // 屏幕高度
#define ICON_WIDTH      56       // 图标宽度
#define ICON_HEIGHT     56       // 图标高度
#define APP_WINDOW_START_X  0    // 应用窗口起始 X
#define APP_WINDOW_START_Y  0    // 应用窗口起始 Y
#define APP_WINDOW_WIDTH    DISPLAY_WIDTH    // 320
#define APP_WINDOW_HEIGHT   DISPLAY_HEIGHT   // 240
```

---

## 8. tinyui GUI 控件参考

所有控件头文件位于 `include/gui/`，按需引入。

### 8.1 View（基类）— `view.h`

所有控件继承自 `View`。通用方法：

```cpp
void setPosition(int x, int y);       // 设置位置
int getX(), getY();                   // 获取位置
int getWidth(), getHeight();          // 获取尺寸
void setSize(int width, int height);  // 设置尺寸
void setVisible(bool visible);        // 设置可见性
bool isVisible();                     // 是否可见

// 像素缓冲区（RGB565 格式）
void setImageBuffer(const uint16_t* buffer);   // 设置背景图片
void setColor(uint16_t color);                 // 设置纯色背景
void setTextColor(uint16_t color);             // 设置文字颜色
void setText(const char* text);                 // 设置居中文字
void setText(const char* text, int sx, int sy); // 设置指定位置文字
void setTransparent(bool transparent);          // 设置透明
void setFontSize(int size);                     // VIEW_SMALL_FONT (0) 或 VIEW_BIG_FONT (1)
void setMaskBuffer(const uint8_t* buffer);      // Alpha 遮罩
void setBoundaryBuffer(const uint16_t* buffer); // 边界裁剪（支持圆角矩形）

// 颜色渐变
void setColorGradient(int type);  // COLOR_GRADIENT_NONE(0), HORIZONTAL(1), VERTICAL(2)
void setGradientStart(uint16_t color);

// Flush：请求重绘指定区域
void flush(int sx, int sy, int w, int h);

// 触摸事件常量
#define TOUCH_EVENT_UP    0x0   // 抬起
#define TOUCH_EVENT_DOWN  0x1   // 按下
#define TOUCH_EVENT_CLICK 0x2   // 点击
```

### 8.2 Window（顶层窗口）— `window.h`

```cpp
// 创建顶层窗口（320x240）
auto win = std::make_shared<Window>(320, 240);
auto win = std::make_shared<Window>(sx, sy, width, height); // 指定起始位置

void addView(std::shared_ptr<View> view, int x, int y, int z_order = -1);
void removeView(std::shared_ptr<View> view);
void removeAllViews();
void setViewZOrder(std::shared_ptr<View> view, int z_order);
void setVisible(bool visible);
bool handleEvent(int pressure, int x, int y);
void flush(int sx, int sy, int w, int h);
```

### 8.3 FrameView（容器框架）— `frame_view.h`

```cpp
auto frame = std::make_shared<FrameView>(width, height);

void addView(std::shared_ptr<View> child, int x, int y);  // 添加子控件（指定位置）
void addChild(std::shared_ptr<View> child);   // 添加子控件
void removeChild(std::shared_ptr<View> child);
void removeAllChilds();                       // 移除所有子控件
void setOnClick(ClickCallback callback);      // 设置点击回调 std::function<void(void*)>
```

### 8.4 LabelView（标签）— `label_view.h`

```cpp
auto label = std::make_shared<LabelView>(width, height);

void setText(const char* text);                              // 设置居中文字
void setText(const char* text, int sx, int sy);              // 设置指定位置文字
void setIcon(const uint16_t* buf, int w, int h);             // 设置图标叠加
void setIconVisible(bool visible);                           // 控制图标可见性
```

### 8.5 ButtonView（按钮）— `button_view.h`

```cpp
auto btn = std::make_shared<ButtonView>(width, height);

void setOnClick(ClickCallback callback);  // 设置点击回调 std::function<void(void*)>
void setBottomLine(bool enable);          // 显示底部分隔线
void setTopLine(bool enable);             // 显示顶部分隔线
void setLineColor(uint16_t color);        // 分隔线颜色
```

### 8.6 ImgButtonView（自锁图片按钮）— `imgbutton_view.h`

自锁式双态切换按钮：

```cpp
auto toggle = std::make_shared<ImgButtonView>(width, height);

void setPressedImage(const uint16_t* img);   // 按下/锁定时的图片
void setReleasedImage(const uint16_t* img);  // 释放时的图片
void setSelfLocking(bool enable);            // true=点击后自锁，false=自动弹起
bool isSelfLocking();                        // 是否自锁模式
bool isLocked();                             // 当前是否锁定
void setLocked(bool status);                 // 手动设置锁定状态
void setOnClick(ClickCallback callback);     // 点击回调
```

### 8.7 CheckboxView（复选框）— `checkbox_view.h`

```cpp
auto cb = std::make_shared<CheckboxView>(width, height);

void setOnClick(ClickCallback callback);  // 设置点击回调
void setChecked(int on_off);              // 1=选中, 0=未选中
int isChecked();                          // 返回当前选中状态
```

### 8.8 TextEditView（文本编辑框）— `textedit_view.h`

```cpp
auto edit = std::make_shared<TextEditView>(width, height);

std::string getText();                   // 获取文本内容
void setText(const char* text);          // 设置文本（继承自 View）
void appendText(const std::string& text); // 追加文本
void backspace();                         // 删除最后一个字符（退格）
void clear();                             // 清空
void setReadOnly(bool readonly);          // 设置只读
void setBackgroundColor(uint16_t color);  // 设置背景色
void setOnFocusCallback(FocusCallback cb);         // 获得焦点回调 std::function<void(TextEditView*)>
void setOnTextChangeCallback(TextChangeCallback cb);// 文本变化回调
```

### 8.9 ListView（列表）— `list_view.h`

虚拟滚动列表，自动复用 Button 池：

```cpp
auto list = std::make_shared<ListView>(width, height);

void addItem(const char* text, int itemId);           // 添加一项
void changeItem(const char* text, int itemId);        // 修改一项的文字
bool removeItem(int itemId);                           // 删除一项
void clearItems();                                     // 清空列表
int getSize();                                         // 获取项数

void setOnItemClick(ItemClickCallback callback);       // 项点击回调 std::function<void(int itemId)>
int getSelectedIndex();                                // 获取选中索引
void setSelected(int index);                           // 设置选中项
int setSelectedIcon(const uint16_t* icon, int w, int h); // 设置选中图标
void setItemIcon(int index, const uint16_t* icon, int w, int h); // 设置项图标

// 样式设置
void setItemMargin(int margin);     // 左右边距
void setItemHeight(int height);     // 行高（默认 32）
int getItemMargin();
int getItemHeight();
```

### 8.10 ProgressBarView（进度条）— `progressbar_view.h`

```cpp
auto bar = std::make_shared<ProgressBarView>(width, height);

void setProgress(int progress);       // 0-100（整数百分比）
void setProgress(float progress);     // 0.0-1.0（浮点百分比）
int getProgress();                    // 获取进度
void setOnValueChange(valueChangeCallback cb); // 值变化回调 std::function<void(int)>
void setProgressColor(uint16_t color); // 进度条颜色
void setBackgroundColor(uint16_t color); // 背景颜色
void setBorderVisible(bool visible);   // 是否显示边框
void setBorderColor(uint16_t color);   // 边框颜色
```

### 8.11 SpinnerView（下拉选择器）— `spinner_view.h`

```cpp
auto spinner = std::make_shared<SpinnerView>(width, height);
spinner->initializeViews();   // 创建后必须调用此方法进行初始化

void addItem(const char* text, int itemId = -1);
void clearItems();
void changeItem(const char* text, int itemId);
bool removeItem(int itemId);
void setItems(const std::vector<std::string>& items);
void setSelectedIndex(int index);
int getSelectedIndex();
const char* getSelectedText();
void setOnItemSelect(ItemSelectCallback cb);  // 选中回调 std::function<void(int, const char*)>
void setButtonVisible(bool visible);          // 显示/隐藏按钮
```

### 8.12 ScrollView（滚动容器）— `scroll_view.h`

水平滚动容器：

```cpp
auto scroll = std::make_shared<ScrollView>(width, height);

void setContentWidth(int totalWidth);    // 设置内容总宽度（可大于可视宽度）
int getContentWidth();
int getScrollOffset();
void setScrollOffset(int offset);
void scrollBy(int deltaX);
void scrollToX(int x);
void setScrollEnabled(bool enabled);
```

### 8.13 DateTimeView（日期时间视图）— `datetime_view.h`

```cpp
auto dt = std::make_shared<DateTimeView>(width, height);

void setTime(int hour, int minute);               // 设置时间
void setDate(int month, int day, int weekday);    // 设置日期（weekday: 0=周日, 1=周一...）
void updateTime();                                 // 触发重绘
```

### 8.14 自定义控件子类模式

继承 `LabelView` 实现自定义渲染（如 BatteryView, WifiView, NearLinkView）：

```cpp
class MyCustomView : public LabelView {
public:
    MyCustomView(int w, int h) : LabelView(w, h) {}

    void setMyState(int state) { my_state_ = state; }

    int getPixel8(int x, int y, uint32_t* buffer) override {
        // 在这里实现自定义像素渲染逻辑
        // 先调用 LabelView::getPixel8() 获取基础渲染结果
        // 再叠加自定义像素
        return 0;
    }
private:
    int my_state_ = 0;
};
```

---

## 9. 文件 I/O（FatFs）

GoldieOS 使用 FatFs 进行 SD 卡文件操作，需引入 `ff.h`。

```cpp
#include "ff.h"

// SD 卡路径始终以 "0:/" 开头
FIL file;
UINT bytes;

// 打开/创建文件
FRESULT res = f_open(&file, "0:/myfile.wav", FA_READ);
res = f_open(&file, "0:/myfile.wav", FA_WRITE | FA_CREATE_ALWAYS);
res = f_open(&file, "0:/myfile.wav", FA_WRITE | FA_OPEN_APPEND);

// 读/写
f_read(&file, buffer, size, &bytes_read);
f_write(&file, buffer, size, &bytes_written);
f_lseek(&file, offset);   // 移动读写位置
f_size(&file);            // 获取文件大小

// 关闭（务必在每次使用后关闭并清零）
f_close(&file);
memset(&file, 0, sizeof(FIL));  // 关闭后清理句柄结构

// 目录操作
DIR dir;
FILINFO info;
f_opendir(&dir, "0:/music/");
while (f_readdir(&dir, &info) == FR_OK && info.fname[0]) {
    // info.fname 为文件名
    // info.fattrib & AM_DIR → 表示是一个目录
}
f_closedir(&dir);
```

---

## 10. ConvAI Bridge（AI 对话 SDK）

```cpp
#include "convai_bridge.h"

// 初始化（由系统完成，应用不需要调用）
convai_bridge_init();

// 获取引擎句柄
convai_engine_t engine = convai_bridge_get_engine();

// 生命周期管理
convai_bridge_start();    // 启动 AI 对话会话
convai_bridge_stop();     // 停止 AI 对话会话
convai_bridge_restart();  // 重启（停止 + 启动）

// 状态查询
convai_status_e status = convai_bridge_get_status();
// 状态类型：IDLE（空闲）, LISTENING（聆听中）, THINKING（思考中）,
//          ANSWERING（回答中）, INTERRUPTED（被打断）, ANSWER_FINISHED（回答完毕）
int speaking = convai_bridge_is_speaking();  // 是否正在说话

// 回调函数注册
convai_bridge_on_status(convai_bridge_status_cb cb);
//   回调签名：void cb(convai_status_e status) — 对话状态变化时触发
convai_bridge_on_event(convai_bridge_event_cb cb);
//   回调签名：void cb(convai_event_code_e event_type, const char* info) — 连接事件
//   事件类型：CONNECTED（已连接）, DISCONNECTED（已断开）, FAILED（失败）
convai_bridge_on_message(convai_bridge_message_cb cb);
//   回调签名：void cb(const char* json_message) — 服务器下发的原始 JSON 消息

// 配置（在 start() 之前调用）
convai_bridge_set_startup_config(const char* json_config);
const char* config = convai_bridge_get_startup_config();

// 音频（通常由 bridge 自动管理）
convai_bridge_send_audio(const uint8_t* data, size_t len, const convai_audio_frame_info_t* info);
convai_bridge_set_audio_source(convai_audio_source_t* src, int sample_rate, int channels, int bits);
```

**AI 应用典型模式（如 AItalk、Settings）：**
1. 通过 `convai_bridge_get_engine()` 获取引擎
2. 注册 status / event / message 回调
3. 调用 `convai_bridge_start()` 开始对话
4. status 回调驱动 UI 状态变化（聆听中→思考中→回答中）
5. message 回调解析 JSON 获取情感/函数调用（function_call）数据
6. 退出时调用 `convai_bridge_stop()`

---

## 11. 完整 App 开发模式

### 11.1 最简应用模板

```cpp
// main_app.cpp
#include "main_ui.h"
extern "C" {
#include "goldie_osal.h"
#include "service_manager.h"
#include "app_manager.h"
#include "event_service.h"
}

static void goldie_app_run(void) {
    main_ui_init();
    // 设置回调、获取服务...
}

static void goldie_app_exit(void) {
    window_exit();
}

static void goldie_app_suspend(void) {
    window_suspend();
}

static void goldie_app_resume(void) {
    window_resume();
}

static void goldie_touch_event(int pressure, int x, int y) {
    if (Window_main) Window_main->handleEvent(pressure, x, y);
}

static void goldie_keyboard_event(int pressure, int key) {
    if ((key == SYSTEM_KEY_VALUE_BACK) && (pressure == 1))
        goldie_exit_app(&my_app);
}

static App_t my_app = {
    "应用名", goldie_app_run, goldie_app_exit,
    goldie_app_suspend, goldie_app_resume,
    goldie_touch_event, goldie_keyboard_event,
    NULL,  // 图标
};

static void my_entry(void) { install_app(&my_app); }
GOLDIE_INIT_CALL_(my_entry);
```

### 11.2 多页面应用模式

使用多个 `FrameView`，通过切换可见性实现页面切换：

```cpp
static void show_page(int page) {
    FrameView_page1->setVisible(page == 0);
    FrameView_page2->setVisible(page == 1);
    FrameView_page3->setVisible(page == 2);
    // 在显示页面前更新页面数据
    if (page == 2) refresh_page2_data();
}

// 在键盘事件中根据当前页面处理返回导航：
static void goldie_keyboard_event(int pressure, int key) {
    if ((key == SYSTEM_KEY_VALUE_BACK) && (pressure == 1)) {
        if (current_page == 0) {
            goldie_exit_app(&my_app);   // 主页面时退出
        } else {
            show_page(--current_page);  // 返回上一页
        }
    }
}
```

### 11.3 后台线程模式

```cpp
static volatile int running_flag = 0;
static volatile int thread_exit_flag = 0;
static volatile int thread_busy_flag = 0;

static int worker_thread(void* arg) {
    while (running_flag) {
        if (work_needed) {
            thread_busy_flag = 1;
            // 执行工作...
            thread_busy_flag = 0;
        }
        goldie_msleep(50);
    }
    thread_exit_flag = 1;
    return 0;
}

// 在 goldie_app_run() 中：
running_flag = 1;
void* handle = goldie_thread_create(worker_thread, NULL, "worker", 0x1000);

// 在 goldie_app_exit() 中：
running_flag = 0;
while (!thread_exit_flag) goldie_msleep(100);   // 等待线程退出标志
goldie_thread_destroy(handle);
```

### 11.4 音频应用模式

```cpp
static AudioService* audio;
static AualgoService* algo;
static volatile int is_recording = 0;
static volatile int record_busy = 0;

// 在 goldie_app_run() 中：
audio = (AudioService*)wait_service(AUDIO_SERVICE_INDEX);
algo = (AualgoService*)get_service(AUALGO_SERVICE_INDEX);
if (algo) algo->puase();  // 录音时暂停唤醒词检测

static int recording_thread(void* arg) {
    short* buf = (short*)goldie_malloc(BUFFER_SIZE);
    while (running_flag) {
        if (is_recording) {
            record_busy = 1;
            int len = audio->audio_read(buf, BUFFER_SIZE);
            if (len > 0) {
                // 处理音频数据...
            }
        } else {
            record_busy = 0;
            goldie_msleep(30);
        }
    }
    goldie_free(buf);
    thread_exit_flag = 1;
    return 0;
}

// 在 goldie_app_exit() 中：
running_flag = 0;
while (record_busy) goldie_msleep(100);     // 等待录音完成
while (!thread_exit_flag) goldie_msleep(100); // 等待线程退出
audio->record_stop();
audio->play_stop();
goldie_thread_destroy(thread_handle);
// 恢复算法服务
if (algo) algo->run();
```

### 11.5 SD 卡文件操作模式

```cpp
static SdCardService* sdcard;

// 在 goldie_app_run() 中：
sdcard = (SdCardService*)wait_service(SDCARD_SERVICE_INDEX);
if (!sdcard->IsSdCardExists()) {
    printf("未检测到 SD 卡！\n");
    return;
}

// 文件操作：
FIL file;
FRESULT res = f_open(&file, "0:/data/file.txt", FA_READ);
if (res == FR_OK) {
    UINT bytes_read;
    char buf[256];
    f_read(&file, buf, sizeof(buf), &bytes_read);
    f_close(&file);
    memset(&file, 0, sizeof(FIL));  // 必须清零
}
```

### 11.6 启动器/应用列表模式

```cpp
#include "app_manager.h"

// 获取所有已安装的应用：
int app_count = 0;
App_t** app_list = get_app_list(&app_count);

// 遍历应用：
for (int i = 0; i < app_count; i++) {
    if (app_list[i]->icon) {
        // 用应用图标创建按钮
        auto btn = std::make_shared<ButtonView>(ICON_WIDTH, ICON_HEIGHT);
        btn->setImageBuffer(app_list[i]->icon);

        App_t* target = app_list[i];
        btn->setOnClick([target](void*) {
            goldie_run_app(target);  // 启动应用
        });
    }
}

// 按名称查找应用：
App_t* found = get_app_by_name("对讲机");
if (found) goldie_run_app(found);

// 启动主屏幕/启动器应用：
start_launcher_app();
```

### 11.7 服务回调注册模式

```cpp
// WiFi 回调：
static void wifi_status_callback(int event, void* data) {
    switch (event) {
        case WIFI_STATUS_STA_CONNECTED:
            update_ui_connected();    // 更新 UI 显示已连接
            break;
        case WIFI_STATUS_STA_DISCONNECTED:
            update_ui_disconnected(); // 更新 UI 显示已断开
            break;
        case WIFI_STATUS_STA_SCANDONE:
            // 从 wifi_service->svr_sta_get_ap_list() 读取 AP 列表
            populate_ap_list();
            break;
    }
}

// 在 goldie_app_run() 中注册：
WifiService* wifi = (WifiService*)wait_service(WIFI_SERVICE_INDEX);
wifi->register_callback(wifi_status_callback);

// SLE 回调：
static void sle_status_callback(SleStatusType status, void* data) {
    switch (status) {
        case SLE_STATUS_DEVICE_LIST_UPDATED:
            update_device_list();   // 更新设备列表
            break;
        case SLE_STATUS_ENABLED:
            // 星闪已启用
            break;
    }
}
```

---

## 12. 关键编码规则与约定

### 12.1 必须遵守
1. **C 头文件必须用 `extern "C" {}` 包裹**，在 C++ 文件中引用 C 头文件时
2. **在 `goldie_app_exit()` 中彻底清理线程**：设置停止标志 → 等待退出标志 → 销毁线程
3. **音频操作前暂停 AualgoService**，退出时**务必恢复**
4. **每次 `f_close()` 后必须 `memset(&file, 0, sizeof(FIL))`** 清理文件句柄
5. **线程间访问共享数据必须加互斥锁**
6. **必须实现全部 6 个生命周期函数**（run、exit、suspend、resume、touch、keyboard）
7. **必须处理 BACK 键 (300)**——通常在 `pressure==1` 时退出应用
8. **通过 EventService 注册广播接收器**以接收系统事件（时间更新、电量变化）

### 12.2 严禁操作
1. **严禁手动修改** `main_ui.h` 的控件结构（它是自动生成的）
2. **严禁在线程退出前调用** `goldie_thread_destroy()`
3. **严禁残留文件句柄**——退出前必须 f_close 所有打开的文件
4. **严禁在主线程中阻塞**——`goldie_app_run()` 和事件处理函数中不能长时间阻塞
5. **严禁跳过互斥锁的解锁**——所有 return 路径都必须解锁

### 12.3 内存管理
- 动态分配使用 `goldie_malloc()` / `goldie_free()`
- 线程栈大小：0x1000 (4KB) 为典型值，重负载音频线程可用 0x2000
- 音频缓冲区计算：`2 × BYTES_PER_FRAME × FRAME_MS × SAMPLERATE / 1000`（如 8kHz/80ms 立体声 = 2560 字节）
- 播放缓冲区：通常 2048 字节

### 12.4 资源图片
- 图片以 C 头文件形式引入，包含 RGB565 格式的 `const uint16_t` 数组
- 引入方式：`#include "rgb16_图片名_宽_高.h"`
- 使用方式：`widget->setImageBuffer((uint16_t*)&rgb16_图片名_宽_高)`
- 边界遮罩用于裁剪：`widget->setBoundaryBuffer(boundary_data)`

---

## 13. 应用功能速查表

| 功能 | API/服务 | 参考应用 |
|------|---------|---------|
| 基础 UI | tinyui 控件 | 全部应用 |
| 音频输入/输出 | AudioService | Recorder, Story, Walkie-Talkie |
| AI 对话 | convai_bridge | AItalk, Settings |
| WiFi 连接 | WifiService | Settings, DualScreen AI |
| 星闪无线 | SleSdpService | Settings, Walkie-Talkie |
| 星闪音频流 | SleWtpService | Walkie-Talkie |
| SD 卡读写 | SdCardService + FatFs | Recorder, Story, Alarm |
| MP3 播放 | Helix 解码器 + AudioService | Story |
| Opus 编解码 | tiny_codec_opus | Recorder |
| 闹钟管理 | AlarmService | Alarm |
| 电池状态 | BatDrv | Launcher |
| 系统事件 | EventService | Launcher, AItalk |
| 应用管理 | AppManager | Launcher |
| 文件 I/O | FatFs (ff.h) | Recorder, Story |
| 多线程 | goldie_osal / Goldie_Thread | AItalk, Recorder, Story |
| 自定义控件 | 继承 LabelView/View | Launcher (Battery/Wifi/NearLink) |
