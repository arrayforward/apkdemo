# GoldieOS 应用开发手册

> 适用平台：HiSilicon WS63 (RISC-V) | 屏幕：320×240 | 语言：C++ | UI框架：tinyui

---

## 目录

1. [平台概述](#1-平台概述)
2. [快速入门：从零开发一个 App](#2-快速入门从零开发一个-app)
3. [应用生命周期详解](#3-应用生命周期详解)
4. [UI 开发指南](#4-ui-开发指南)
5. [系统服务详解](#5-系统服务详解)
6. [驱动访问](#6-驱动访问)
7. [OS 抽象层 (OSAL)](#7-os-抽象层-osal)
8. [文件 I/O 与 SD 卡](#8-文件-io-与-sd-卡)
9. [AI 对话 SDK（ConvAI Bridge）](#9-ai-对话-sdkconvai-bridge)
10. [完整开发模式与示例](#10-完整开发模式与示例)
11. [编码规范与最佳实践](#11-编码规范与最佳实践)
12. [已有 App 参考索引](#12-已有-app-参考索引)

---

## 1. 平台概述

### 1.1 硬件配置

| 组件 | 规格 |
|------|------|
| 主控芯片 | HiSilicon WS63 (RISC-V) |
| 屏幕 | 320×240 彩色 LCD |
| 输入 | 触摸屏 + GPIO 按键 |
| 无线通信 | WiFi (STA/SoftAP) + 星闪 SLE |
| 音频 | ES8311 编解码器，支持录音/播放 |
| 存储 | SD 卡 (FatFs) |
| 电源 | 锂电池，带电量检测 |

### 1.2 软件架构

```
┌─────────────────────────────────────────────────┐
│                    应用层 (Apps)                  │
│   AItalk  Alarm  Recorder  Story  Settings ...  │
├─────────────────────────────────────────────────┤
│              tinyui GUI 框架 (C++)               │
│  Window FrameView ButtonView ListView ...       │
├─────────────────────────────────────────────────┤
│              系统服务层 (Service)                │
│  AudioService EventService WifiService ...      │
├─────────────────────────────────────────────────┤
│        SDK 集成层 (convai_bridge)                │
├─────────────────────────────────────────────────┤
│            OS 抽象层 (OSAL)                      │
│   线程 互斥锁 信号量 内存 时间 GPIO ADC          │
├─────────────────────────────────────────────────┤
│            硬件驱动层 (Driver)                   │
│  I2C DISP I2S TOUCH KEYBOARD BATTERY WLAN ...   │
├─────────────────────────────────────────────────┤
│              硬件平台 (WS63)                     │
└─────────────────────────────────────────────────┘
```

各层之间通过**服务管理器 (ServiceManager)** 和**驱动管理器 (DriverManager)** 解耦。应用通过 `get_service(SERVICE_INDEX)` / `wait_service(SERVICE_INDEX)` 获取服务指针，服务间通过 `EventService` 的广播机制通信。

### 1.3 项目目录结构

```
goldieos/
├── include/
│   ├── core/           # 核心框架头文件
│   │   ├── app_manager.h      # 应用管理器
│   │   ├── service_manager.h  # 服务管理器
│   │   ├── audio_service.h    # 音频服务
│   │   ├── event_service.h    # 事件服务
│   │   ├── wifi_service.h     # WiFi 服务
│   │   ├── sdcard_service.h   # SD 卡服务
│   │   ├── sle_sdp_service.h  # 星闪服务
│   │   ├── sle_wtp_service.h  # 星闪对讲服务
│   │   ├── driver_manager.h   # 驱动管理器
│   │   └── input_service.h    # 输入服务
│   ├── gui/            # tinyui 控件头文件 (26个)
│   ├── osal/           # OS 抽象层
│   │   ├── goldie_osal.h      # 线程/锁/内存/时间
│   │   ├── goldie_thread.h    # C++ 线程封装
│   │   └── goldie_display.h   # 显示常量
│   └── services/       # 服务接口头文件
│       ├── alarm/alarm_service.h
│       ├── audio/aualgo_service.h
│       └── ntp/ntp_service.h
├── apps/               # ★ 所有应用代码放这里
│   ├── AItalk/         # AI 对话助手
│   ├── alarm/          # 闹钟
│   ├── launcher/       # 桌面启动器
│   ├── recorder/       # 录音机
│   ├── settings/       # 系统设置
│   ├── Story/          # MP3 故事机
│   ├── Walkie-talkie/  # 对讲机
│   └── ...
├── sdk_integration/    # convai_bridge（AI SDK 桥接）
├── platform/           # 平台适配层
├── services/           # 服务实现
└── drivers/            # 驱动实现
```

每个 App 目录的标准结构：
```
apps/MyApp/
├── main_app.cpp        # 应用逻辑（生命周期、业务代码）
├── main_ui.h           # UI 布局（tinyui 工具生成）
└── assets/             # 图片资源（RGB565 C 头文件）
    ├── rgb16_xxx_w_h.h
    └── ...
```

---

## 2. 快速入门：从零开发一个 App

### 2.1 最简 App 示例

下面是一个显示"Hello GoldieOS"文本的最简应用完整代码。

**Step 1：编写 main_ui.h**

```cpp
#ifndef INCLUDE_MAIN_UI_H
#define INCLUDE_MAIN_UI_H
#include <memory>
#include "goldie_display.h"
#include "window.h"
#include "frame_view.h"
#include "label_view.h"

static std::shared_ptr<LabelView> LabelView_hello;
static std::shared_ptr<FrameView> FrameView_main;
static std::shared_ptr<Window> Window_main;

static void main_ui_init() {
    // 创建 Label，320×40 居中显示"Hello GoldieOS"
    LabelView_hello = std::make_shared<LabelView>(320, 40);
    LabelView_hello->setColor(0x0000);    // 黑色背景
    LabelView_hello->setTextColor(0xFFFF); // 白色文字
    LabelView_hello->setText("Hello GoldieOS");
    LabelView_hello->setVisible(true);

    // 创建容器
    FrameView_main = std::make_shared<FrameView>(320, 240);
    FrameView_main->setColor(0x0000);
    FrameView_main->setVisible(true);

    // 创建窗口
    Window_main = std::make_shared<Window>(0, 0, 320, 240);
    Window_main->setVisible(true);

    // 建立层级：Window → FrameView → LabelView
    FrameView_main->addView(LabelView_hello, 0, 100);  // y=100 垂直居中
    Window_main->addView(FrameView_main, 0, 0);

    // 刷新显示
    Window_main->flush(0, 0, 320, 240);
}

static void window_exit() {
    Window_main->setVisible(false);
    Window_main->removeAllViews();
    FrameView_main->removeAllChilds();
    LabelView_hello.reset();
    FrameView_main.reset();
    Window_main.reset();
}

static void window_suspend() {
    Window_main->setVisible(false);
}

static void window_resume() {
    Window_main->setVisible(true);
    Window_main->flush(0, 0, 320, 240);
}
#endif
```

**Step 2：编写 main_app.cpp**

```cpp
#include "main_ui.h"
extern "C" {
#include "goldie_osal.h"
#include "service_manager.h"
#include "app_manager.h"
}

static void goldie_app_run(void) {
    main_ui_init();
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

// ★ 核心：定义 App_t 实例
static App_t my_app = {
    "Hello",                    // app_name
    goldie_app_run,             // h_app_run
    goldie_app_exit,            // h_app_exit
    goldie_app_suspend,         // h_app_suspend
    goldie_app_resume,          // h_app_resume
    goldie_touch_event,         // h_touch_event
    goldie_keyboard_event,      // h_keyboard_event
    NULL,                       // icon（NULL 表示不在启动器中显示）
};

// ★ 核心：注册入口
static void hello_entry(void) {
    install_app(&my_app);
}
GOLDIE_INIT_CALL_(hello_entry);
```

### 2.2 关键概念说明

**App_t 结构体** 是应用的"身份证"。包含 8 个字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `app_name` | `char[32]` | 应用名称，会在启动器中显示 |
| `h_app_run` | 函数指针 | 应用启动时被调用，初始化入口 |
| `h_app_exit` | 函数指针 | 应用退出时被调用，清理资源 |
| `h_app_suspend` | 函数指针 | 切到后台时被调用 |
| `h_app_resume` | 函数指针 | 回到前台时被调用 |
| `h_touch_event` | 函数指针 | 触摸事件分发 |
| `h_keyboard_event` | 函数指针 | 键盘事件分发 |
| `icon` | `uint16_t*` | 56×56 RGB565 图标（NULL 则不显示） |

**GOLDIE_INIT_CALL_(func)** 是一个宏，将函数 `func` 注册为系统启动时自动执行的初始化函数。在 WS63 平台上展开为 `app_run(func)`。

**install_app(&my_app)** 将应用注册到应用管理器，使其可被启动器发现和启动。

---

## 3. 应用生命周期详解

### 3.1 生命周期状态机

```
         ┌──────────┐
         │  NONE    │  初始状态
         └────┬─────┘
              │ goldie_run_app()
              ▼
         ┌──────────┐
    ┌───→│ RUNNING  │←─────────────┐
    │    └────┬─────┘              │
    │         │ goldie_suspend_app()│
    │         ▼                    │
    │    ┌──────────┐   goldie_resume_app()
    │    │SUSPENDED │──────────────┘
    │    └────┬─────┘
    │         │ goldie_exit_app()
    │         ▼
    │    ┌──────────┐
    └────│ EXITED   │  可被再次启动
         └──────────┘
```

### 3.2 各阶段详解

#### goldie_app_run() —— 启动阶段

```cpp
static void goldie_app_run(void) {
    // === 步骤 1：获取所需服务 ===
    AudioService* audio = (AudioService*)wait_service(AUDIO_SERVICE_INDEX);
    EventService* evt = (EventService*)wait_service(EVENT_SERVICE_INDEX);
    SdCardService* sd = (SdCardService*)wait_service(SDCARD_SERVICE_INDEX);

    // === 步骤 2：暂停冲突服务 ===
    // 如果应用需要进行音频操作，暂停唤醒词检测
    AualgoService* algo = (AualgoService*)get_service(AUALGO_SERVICE_INDEX);
    if (algo) algo->puase();

    // === 步骤 3：初始化 UI ===
    main_ui_init();

    // === 步骤 4：注册回调 ===
    button->setOnClick(on_button_click);
    evt->register_broadcast_recv(EVENT_BROADCAST_POWER, on_power_update);

    // === 步骤 5：创建后台线程（如果需要） ===
    running_flag = 1;
    thread_handle = goldie_thread_create(worker_thread, NULL, "worker", 0x1000);

    // === 步骤 6：设置运行标志 ===
    app_status = 1;
}
```

#### goldie_app_exit() —— 退出阶段（最重要！）

退出阶段的清理顺序极其关键，必须严格遵守：

```cpp
static void goldie_app_exit(void) {
    // === 1. 优先停止标志（让线程自然退出） ===
    running_flag = 0;
    is_recording = 0;
    is_playing = 0;

    // === 2. 停止音频 ===
    if (audio_service) {
        audio_service->record_stop();
        audio_service->play_stop();
    }

    // === 3. 关闭文件 ===
    if (record_file.fs) {
        f_close(&record_file);
        memset(&record_file, 0, sizeof(FIL));
    }

    // === 4. 等待线程退出（轮询退出标志） ===
    while (!play_thread_exit_flag)  goldie_msleep(100);
    while (!record_thread_exit_flag) goldie_msleep(100);

    // === 5. 销毁线程 ===
    if (recording_task_handle) goldie_thread_destroy(recording_task_handle);
    if (playback_task_handle)  goldie_thread_destroy(playback_task_handle);

    // === 6. 注销回调 ===
    // （如 convai_bridge 的 on_status/on_event/on_message）

    // === 7. 清理 UI ===
    window_exit();

    // === 8. 恢复暂停的服务 ===
    if (algo_service) algo_service->run();
}
```

#### goldie_app_suspend() / goldie_app_resume() —— 前后台切换

```cpp
static void goldie_app_suspend(void) {
    // 停止动画、暂停播放等
    animation_running = 0;
    // 隐藏窗口
    window_suspend();
}

static void goldie_app_resume(void) {
    // 显示窗口并刷新
    window_resume();
    // 恢复动画、刷新数据
    animation_running = 1;
    refresh_data();
}
```

### 3.3 按键事件处理

系统定义了预置按键值：

```cpp
// 定义在 input_service.h
typedef enum SYSTEM_KEY_VALUE {
    SYSTEM_KEY_VALUE_BACK   = 300,  // 返回键
    SYSTEM_KEY_VALUE_WAKEUP = 301,  // 唤醒/功能键
} SYSTEM_KEY_VALUE;
```

按键处理的标准模式：

```cpp
static void goldie_keyboard_event(int pressure, int key) {
    // pressure=1 表示按下，0 表示释放
    // 过滤重复的 press 事件

    // 返回键 — 退出应用
    if ((key == SYSTEM_KEY_VALUE_BACK) && (pressure == 1)) {
        goldie_exit_app(&my_app);
        return;
    }

    // 唤醒键 — 可根据应用需求自定义功能
    // 例如对讲机用它做 PTT 按钮
    if ((key == SYSTEM_KEY_VALUE_WAKEUP) && (pressure == 1)) {
        start_transmit();
    }
    if ((key == SYSTEM_KEY_VALUE_WAKEUP) && (pressure == 0)) {
        stop_transmit();
    }
}
```

### 3.4 触摸事件处理

```cpp
static void goldie_touch_event(int pressure, int x, int y) {
    // 标准做法：将触摸事件直接分发给 Window
    if (Window_main) {
        Window_main->handleEvent(pressure, x, y);
    }
}
```

Window 会自动将触摸事件按 z-order 分发给子控件，子控件根据坐标判断是否命中并触发其注册的 OnClick/OnItemClick 等回调。

---

## 4. UI 开发指南

### 4.1 tinyui 框架概述

tinyui 是一个轻量级的即时模式 GUI 框架，所有控件都是 C++ 类，通过 `std::shared_ptr` 管理生命周期。

**控件继承关系：**

```
View (基类)
├── Window (顶层窗口, 320×240)
├── FrameView (容器框架)
│   ├── ListView (虚拟滚动列表)
│   ├── SpinnerView (下拉选择器)
│   └── ScrollView (水平滚动容器)
├── LabelView (标签)
│   ├── ButtonView (按钮)
│   └── DateTimeView (日期时间)
├── ImgButtonView (自锁图片按钮)
├── CheckboxView (复选框)
├── TextEditView (文本编辑框)
└── ProgressBarView (进度条)
```

### 4.2 控件通用 API（View 基类）

所有控件都继承自 `View`，拥有以下通用方法：

```cpp
// === 基本属性 ===
void setPosition(int x, int y);    // 设置相对父控件的坐标
int getX();                         // 获取 X 坐标
int getY();                         // 获取 Y 坐标
int getWidth();                     // 获取宽度
int getHeight();                    // 获取高度
void setSize(int w, int h);        // 调整尺寸
void setVisible(bool visible);     // 显示/隐藏
bool isVisible();                   // 是否可见

// === 外观设置 ===
void setColor(uint16_t color);                 // 纯色背景 (RGB565)
void setImageBuffer(const uint16_t* buf);      // 图片背景 (RGB565 数组)
void setTextColor(uint16_t color);             // 文字颜色
void setText(const char* text);                // 居中文字
void setText(const char* text, int sx, int sy); // 指定位置文字
void setTransparent(bool transparent);          // 透明背景
void setFontSize(int size);                     // VIEW_SMALL_FONT(0) / VIEW_BIG_FONT(1)
void setColorGradient(int type);                // 渐变色 COLOR_GRADIENT_HORIZONTAL/VERTICAL
void setGradientStart(uint16_t color);          // 渐变起始色

// === 裁剪与遮罩 ===
void setMaskBuffer(const uint8_t* buf);         // Alpha 遮罩
void setBoundaryBuffer(const uint16_t* buf);    // 边界裁剪（支持圆角矩形）

// === 渲染 ===
void flush(int sx, int sy, int w, int h);      // 请求重绘指定区域
```

**RGB565 颜色格式**：`0b RRRRRGGGGGGBBBBB`（5位红 + 6位绿 + 5位蓝）

常用颜色：
```
0x0000 = 黑色    0xFFFF = 白色    0xF800 = 红色
0x07E0 = 绿色    0x001F = 蓝色    0xFFE0 = 黄色
```

### 4.3 Window（顶层窗口）

```cpp
// 构造方法
auto win = std::make_shared<Window>(320, 240);
auto win = std::make_shared<Window>(start_x, start_y, width, height);

// 方法
void addView(shared_ptr<View> view, int x, int y, int z_order = -1);
void removeView(shared_ptr<View> view);
void removeAllViews();
void setViewZOrder(shared_ptr<View> view, int z_order);
void setVisible(bool visible);
bool handleEvent(int pressure, int x, int y);   // 触摸事件分发
void flush(int sx, int sy, int w, int h);       // 区域重绘
```

**典型用法：**
```cpp
Window_main = std::make_shared<Window>(0, 0, 320, 240);
Window_main->setColor(0x0000);

// 将 FrameView 添加到窗口
Window_main->addView(FrameView_main, 0, 0);

// 全屏刷新
Window_main->flush(0, 0, 320, 240);

// 局部刷新（性能优化）
Window_main->flush(100, 50, 120, 30);
```

### 4.4 FrameView（容器框架）

用于页面组织和子控件分组。支持背景色、图片、点击回调。

```cpp
auto frame = std::make_shared<FrameView>(320, 200);

void addView(shared_ptr<View> child, int x, int y);  // 添加到指定坐标
void addChild(shared_ptr<View> child);                // 添加到子控件列表
void removeChild(shared_ptr<View> child);
void removeAllChilds();

// 点击回调：点击 FrameView 内部且未被子控件消费时触发
void setOnClick(function<void(void*)> callback);
```

**多页面模式（核心模式）：**

```cpp
// 定义多个 FrameView 作为"页面"
static shared_ptr<FrameView> FrameView_page1;
static shared_ptr<FrameView> FrameView_page2;
static shared_ptr<FrameView> FrameView_page3;
static int current_page = 0;

// 页面切换函数
static void show_page(int page) {
    FrameView_page1->setVisible(page == 0);
    FrameView_page2->setVisible(page == 1);
    FrameView_page3->setVisible(page == 2);

    switch (page) {
        case 0: refresh_main_data(); break;
        case 1: refresh_settings_data(); break;
        case 2: refresh_about_data(); break;
    }

    current_page = page;
    Window_main->flush(0, 0, 320, 240);
}

// 在初始化时创建页面并全部可见=false（除了首页）
static void main_ui_init() {
    FrameView_page1 = std::make_shared<FrameView>(320, 240);
    // ... 添加控件到 page1 ...
    FrameView_page1->setVisible(true);   // 首页可见

    FrameView_page2 = std::make_shared<FrameView>(320, 240);
    // ... 添加控件到 page2 ...
    FrameView_page2->setVisible(false);  // 其他页隐藏

    Window_main->addView(FrameView_page1, 0, 0);
    Window_main->addView(FrameView_page2, 0, 0);
}

// 返回键处理带页面感知
static void goldie_keyboard_event(int pressure, int key) {
    if ((key == SYSTEM_KEY_VALUE_BACK) && (pressure == 1)) {
        if (current_page > 0) {
            show_page(current_page - 1);  // 返回上一页
        } else {
            goldie_exit_app(&my_app);     // 首页退出
        }
    }
}
```

### 4.5 LabelView（文本标签）

```cpp
auto label = std::make_shared<LabelView>(200, 30);

void setText(const char* text);                          // 居中文字
void setText(const char* text, int sx, int sy);          // 指定起始坐标文字
void setIcon(const uint16_t* icon_buf, int w, int h);    // 叠加图标
void setIconVisible(bool visible);
```

**示例：带图标的标签**
```cpp
auto title = std::make_shared<LabelView>(300, 40);
title->setColor(0x001F);              // 蓝色背景
title->setTextColor(0xFFFF);          // 白色文字
title->setText("设置");
title->setIcon(settings_icon, 24, 24); // 32x24 图标
title->setIconVisible(true);
```

### 4.6 ButtonView（按钮）

```cpp
auto btn = std::make_shared<ButtonView>(100, 40);

// 核心：设置点击回调
btn->setOnClick([](void* context) {
    printf("按钮被点击了！\n");
});

// 分隔线装饰
btn->setBottomLine(true);     // 显示底部分隔线（常用于列表项）
btn->setTopLine(true);        // 显示顶部分隔线
btn->setLineColor(0x8410);    // 分隔线颜色
```

**示例：应用图标按钮**
```cpp
// 创建一个带图标的 56×56 圆角按钮
auto app_btn = std::make_shared<ButtonView>(56, 56);
app_btn->setImageBuffer(app_icon_data);
app_btn->setBoundaryBuffer(round_rect_boundary);  // 圆角裁剪

// Lambda 捕获目标应用指针
App_t* target = app_list[i];
app_btn->setOnClick([target](void*) {
    goldie_run_app(target);  // 启动目标应用
});
```

### 4.7 ImgButtonView（自锁图片按钮）

双态切换按钮，常用于开关控件。点击后自动在"按下"和"释放"两种状态间切换。

```cpp
auto toggle = std::make_shared<ImgButtonView>(60, 30);

// 设置两种状态的图片
toggle->setPressedImage(switch_on_image);   // 开启状态的图片
toggle->setReleasedImage(switch_off_image);  // 关闭状态的图片

// 自锁模式：点击后保持按下，再次点击后弹起
toggle->setSelfLocking(true);

// 手动控制状态
toggle->setLocked(true);   // 手动设置为开
bool state = toggle->isLocked();  // 获取当前状态

// 点击回调
toggle->setOnClick([](void* ctx) {
    ImgButtonView* btn = (ImgButtonView*)ctx;
    if (btn->isLocked()) {
        // 闹钟已开启
    } else {
        // 闹钟已关闭
    }
});
```

**Alarm 应用的实际示例：**
```cpp
// 闹钟开关（自锁按钮）
auto switch_btn = std::make_shared<ImgButtonView>(64, 32);
switch_btn->setPressedImage((uint16_t*)&switch_on_64_32);
switch_btn->setReleasedImage((uint16_t*)&switch_off_64_32);
switch_btn->setSelfLocking(true);

switch_btn->setOnClick([&](void* context) {
    // 切换当前编辑闹钟的启用状态
    alarm_list_for_display[current_edit_index].enabled = switch_btn->isLocked();
});
```

### 4.8 ListView（虚拟滚动列表）

ListView 实现虚拟滚动，自动复用按钮池。适合展示大量条目。

```cpp
auto list = std::make_shared<ListView>(280, 200);

// 添加数据项
list->addItem("闹钟 1 - 07:00", 0);   // (显示文本, 唯一ID)
list->addItem("闹钟 2 - 12:30", 1);

// 修改/删除
list->changeItem("闹钟 1 - 07:30", 0);
list->removeItem(1);
list->clearItems();

int total = list->getSize();

// 选中管理
list->setSelected(0);
int idx = list->getSelectedIndex();

// ★ 核心：列表项点击回调
list->setOnItemClick([](int itemId) {
    printf("用户点击了 itemId=%d\n", itemId);
    // itemId 是 addItem 时传入的 ID
});

// 样式
list->setItemHeight(40);    // 行高（默认 32）
list->setItemMargin(20);    // 左右边距（默认 24）
```

**Launcher 中填充应用的示例：**
```cpp
// 遍历应用列表，逐条添加
for (int i = 0; i < app_count; i++) {
    if (app_list[i]->icon) {
        char display_name[64];
        snprintf(display_name, sizeof(display_name), "%s", app_list[i]->app_name);
        list->addItem(display_name, i);
        list->setItemIcon(i, app_list[i]->icon, 24, 24);
    }
}

list->setOnItemClick([](int itemId) {
    goldie_run_app(app_list[itemId]);
});
```

### 4.9 ScrollView（水平滚动容器）

```cpp
auto scroll = std::make_shared<ScrollView>(320, 120);

// 关键：设置内容总宽度（可以大于 320 使能滚动）
scroll->setContentWidth(total_items * (56 + 24));

// 添加子控件（x 坐标可以超出可视区域）
scroll->addView(icon_btn, x_position, y_center);

// 手动控制滚动
scroll->setScrollOffset(100);   // 滚动到偏移 100px
scroll->scrollToX(200);         // 使 x=200 可见
```

**Launcher 应用图标网格的示例：**
```cpp
int total_width = 16 + app_count * (56 + 24);  // 起始偏移 + N×(图标宽+间距)
scroll->setContentWidth(total_width);

int x = 16;
for (int i = 0; i < app_count; i++) {
    auto btn = std::make_shared<ButtonView>(56, 56);
    btn->setImageBuffer(app_list[i]->icon);
    scroll->addView(btn, x, 32);   // x 坐标递增
    x += 56 + 24;                   // 56 图标宽度 + 24 间距
}
```

### 4.10 ProgressBarView（进度条）

```cpp
auto bar = std::make_shared<ProgressBarView>(280, 20);

// 设置进度
bar->setProgress(45);          // 整数 0-100
bar->setProgress(0.45f);       // 浮点 0.0-1.0

// 值变化回调（用户拖动时触发）
bar->setOnValueChange([](int value) {
    printf("音量: %d%%\n", value);
    audio_service->set_volume(AUDIO_PLAY_STREAM_USER, value / 100.0f);
});

// 样式
bar->setProgressColor(0xF800);    // 红色进度
bar->setBackgroundColor(0x8410);  // 灰色背景
bar->setBorderVisible(true);
bar->setBorderColor(0x0000);
```

**录音机播放进度的示例：**
```cpp
// 播放线程中更新进度
if (playback_file_size > 0) {
    int progress = (int)((float)playback_position / playback_file_size * 100);
    if (progress > 100) progress = 100;
    ProgressBarView_main->setProgress(progress);
}
```

### 4.11 CheckboxView（复选框）

```cpp
auto cb = std::make_shared<CheckboxView>(80, 30);

cb->setChecked(1);  // 默认选中
int state = cb->isChecked();

cb->setOnClick([](void* ctx) {
    CheckboxView* cb = (CheckboxView*)ctx;
    printf("复选框状态: %d\n", cb->isChecked());
});
```

**闹钟应用中设置星期几的示例：**
```cpp
// 7 个复选框对应周一到周日
static CheckboxView* weekday_cbs[7];
static bool weekday_values[7] = { true, true, true, true, true, true, true };

for (int i = 0; i < 7; i++) {
    weekday_cbs[i] = std::make_shared<CheckboxView>(24, 24);
    weekday_cbs[i]->setChecked(weekday_values[i]);

    // 用 Lambda 捕获 i
    weekday_cbs[i]->setOnClick([i](void* ctx) {
        weekday_values[i] = ((CheckboxView*)ctx)->isChecked();
    });
}
```

### 4.12 TextEditView（文本编辑框）

```cpp
auto edit = std::make_shared<TextEditView>(200, 30);

void setText(const char* text);         // 设置文本
string text = edit->getText();          // 获取文本
void appendText(const string& text);    // 追加文本
void backspace();                       // 退格
void clear();                           // 清空
void setReadOnly(bool readonly);        // 只读模式
void setBackgroundColor(uint16_t color); // 背景色

// 焦点回调
edit->setOnFocusCallback([](TextEditView* view) {
    // 获得焦点时显示输入法
});
```

### 4.13 SpinnerView（下拉选择器）

```cpp
auto spinner = std::make_shared<SpinnerView>(200, 30);
spinner->initializeViews();   // ★ 创建后必须调用

spinner->addItem("选项A", 0);
spinner->addItem("选项B", 1);
spinner->addItem("选项C", 2);
spinner->setSelectedIndex(0);

// 选中回调
spinner->setOnItemSelect([](int index, const char* text) {
    printf("选中: %d - %s\n", index, text);
});

// 批量设置
vector<string> items = {"主机模式", "从机模式"};
spinner->setItems(items);

// 样式控制
spinner->setButtonVisible(true);   // 是否显示下拉按钮
```

### 4.14 DateTimeView（日期时间显示）

```cpp
auto dt = std::make_shared<DateTimeView>(160, 96);

dt->setTime(14, 30);                     // 14:30
dt->setDate(7, 19, 6);                   // 7月19日 周六
dt->updateTime();                        // 触发重绘
```

**Launcher 中接收时间广播并更新的示例：**
```cpp
// 注册时间广播
evt_service->register_broadcast_recv(EVENT_BROADCAST_TIMERUPDATE, on_time_update);

static void on_time_update(BroadcastMessage* msg) {
    if (msg->msg && msg->msg_len == sizeof(struct tm)) {
        struct tm* t = (struct tm*)msg->msg;

        // UTC+8 转换
        int hour = t->tm_hour + 8;
        if (hour >= 24) hour -= 24;

        DateTimeView_main->setDate(t->tm_mon + 1, t->tm_mday, t->tm_wday);
        DateTimeView_main->setTime(hour, t->tm_min);
        DateTimeView_main->updateTime();
    }
}
```

### 4.15 自定义控件——扩展 LabelView

当标准控件无法满足需求时，可以继承 `LabelView` 并重写 `getPixel8()` 来实现自定义渲染。

**BatteryView 示例（电量图标）：**
```cpp
class BatteryView : public LabelView {
public:
    BatteryView(int w, int h) : LabelView(w, h) {}

    void setBatteryLevel(int level) {
        battery_level_ = level;
        // 触发重绘
        if (getParent()) {
            auto win = std::dynamic_pointer_cast<Window>(getParent());
            if (win) win->flush(getX(), getY(), getWidth(), getHeight());
        }
    }

    void setCharging(bool charging) {
        is_charging_ = charging;
    }

    int getPixel8(int x, int y, uint32_t* buffer) override {
        // 先调用基类渲染背景
        int ret = LabelView::getPixel8(x, y, buffer);
        if (ret == 0) return 0;  // buffer 无效

        // 在指定区域绘制电量指示条
        if (x >= 4 && x <= 4 + battery_level_/2 && y >= 2 && y <= 13) {
            uint32_t color = is_charging_ ? 0x07E007E0 : 0xF800F800;
            // 将颜色写入 buffer（需考虑大端/字节序）
            *((uint32_t*)buffer) = color;
        }
        return 1;
    }

private:
    int battery_level_ = 100;
    bool is_charging_ = false;
};
```

---

## 5. 系统服务详解

### 5.1 服务管理机制

GoldieOS 的服务采用**集中注册、按需获取**的模式。系统启动时各服务模块依次初始化并注册到 ServiceManager，应用运行时通过索引获取服务指针。

```cpp
// === 获取服务的两种方式 ===

// 方式一：阻塞等待（适用于必需的服务）
AudioService* audio = (AudioService*)wait_service(AUDIO_SERVICE_INDEX);
// 此函数会阻塞直到服务就绪，通常在 goldie_app_run() 中使用

// 方式二：非阻塞获取（可能返回 NULL）
AualgoService* algo = (AualgoService*)get_service(AUALGO_SERVICE_INDEX);
// 适用于非必需的服务，调用前需要判空
if (algo) algo->puase();
```

**服务索引速查表：**

| 索引 | 常量 | 服务 | 获取方式 |
|------|------|------|----------|
| 0 | `AUDIO_SERVICE_INDEX` | 音频服务 | `wait_service` |
| 1 | `EVENT_SERVICE_INDEX` | 事件/广播服务 | `wait_service` |
| 2 | `WIFI_SERVICE_INDEX` | WiFi 服务 | `wait_service` |
| 3 | `SDCARD_SERVICE_INDEX` | SD 卡服务 | `wait_service` |
| 4 | `CLOUD_SERVICE_INDEX` | 云服务（已废弃） | 改用 convai_bridge |
| 5 | `AUALGO_SERVICE_INDEX` | 音频算法/唤醒词 | `get_service` |
| 6 | `SLE_SDP_SERVICE_INDEX` | 星闪无线 | `wait_service` |
| 7 | `NTP_SERVICE_INDEX` | NTP 时间同步 | `wait_service` |
| 8 | `ALARM_SERVICE_INDEX` | 闹钟服务 | `get_service` |
| 9 | `SLE_WTP_SERVICE_INDEX` | 星闪对讲传输 | `wait_service` |

### 5.2 AudioService（音频服务）

**这是最复杂且最常用的服务之一。** 音频数据格式为立体声 16-bit PCM。

```cpp
AudioService* audio = (AudioService*)wait_service(AUDIO_SERVICE_INDEX);

// ========== 基础 I/O ==========

// 音频输入（录音）
int read_len = audio->audio_read(short* buffer, unsigned int buf_size);
// 返回实际读取的字节数，失败返回 0 或负数
// 数据格式：立体声交错 16-bit PCM (L0,R0, L1,R1, ...)
// 典型参数：8kHz × 80ms × 2声道 × 2字节 = 2560 字节

// 音频输出（播放）
audio->audio_write(const void* buffer, unsigned int byte_len);
// 注意：写入的是立体声数据

// ========== 流控制 ==========
audio->record_start();   // 开始录音流
audio->record_stop();    // 停止录音流
audio->play_start();     // 开始播放流
audio->play_stop();      // 停止播放流

// ========== 铃声（系统预置音效） ==========
// ring_id: NOTIFY=0, POWERON=1, WAKEUP=2, SLEEP=3, ALARM=4
audio->play_ring(AUDIO_RING_ID_POWERON, 0);  // 播放开机音效（不循环）
audio->play_ring(AUDIO_RING_ID_ALARM, 1);    // 播放闹铃（循环）
audio->stop_ring();   // 停止铃声
audio->wait_ring();   // 阻塞等待当前铃声播放完毕

// ========== 音量 ==========
// stream_type: SYSTEM(0)=铃声, USER(1)=媒体
audio->set_volume(AUDIO_PLAY_STREAM_USER, 0.8f);  // 媒体音量 80%
float vol = audio->get_volume(AUDIO_PLAY_STREAM_USER);
```

**立体声转单声道（常见操作）：**
```cpp
short stereo_buf[2560];  // 立体声缓冲区
int total_bytes = audio->audio_read(stereo_buf, sizeof(stereo_buf));
int sample_count = total_bytes / sizeof(short);
int mono_samples = sample_count / 2;  // 每两个 sample 合成一个

short mono_buf[1280];  // 单声道缓冲区
for (int i = 0; i < mono_samples; i++) {
    mono_buf[i] = stereo_buf[i * 2];       // 只取左声道
    // 或者：mono_buf[i] = (stereo_buf[i*2] + stereo_buf[i*2+1]) / 2;  // 混合
}
```

**完整录音示例：**
```cpp
#define SAMPLE_RATE   8000
#define FRAME_MS      80
#define BYTES_PER_FRAME 2
#define RECORD_BUF_SIZE (2 * BYTES_PER_FRAME * FRAME_MS * SAMPLE_RATE / 1000)  // 2560

static int recording_thread(void* arg) {
    short* buf = (short*)goldie_malloc(RECORD_BUF_SIZE);

    audio->record_start();  // 启动录音流

    while (is_recording) {
        int len = audio->audio_read(buf, RECORD_BUF_SIZE);
        if (len > 0) {
            int mono_count = (len / sizeof(short)) / 2;
            for (int i = 0; i < mono_count; i++) {
                buf[i] = buf[i * 2];  // 左声道→单声道
            }
            // 将 mono_count 个 16-bit sample 写入文件...
            f_write(&file, buf, mono_count * sizeof(short), &written);
        }
        goldie_msleep(10);
    }

    audio->record_stop();  // 停止录音流
    goldie_free(buf);
    return 0;
}
```

### 5.3 EventService（事件/广播服务）

EventService 是应用之间和应用与系统之间通信的核心机制。

```cpp
EventService* evt = (EventService*)wait_service(EVENT_SERVICE_INDEX);

// ========== 广播消息（发布-订阅模式） ==========

// 注册广播接收者
int8_t ret = evt->register_broadcast_recv(broadcast_id, callback);
// broadcast_id: 关注的事件类型
// callback: 收到广播时的回调函数
// 返回 -1 表示注册失败（已满）

// 发送广播
BroadcastMessage msg = {0};
msg.id = EVENT_BROADCAST_POWER;    // 广播类型
msg.ext[0] = battery_level;         // 扩展数据
evt->send_broadcast(&msg);

// ========== 广播消息结构 ==========
typedef struct {
    uint16_t id;            // 广播 ID
    void* msg;              // 数据指针
    uint16_t msg_len;       // 数据长度
    unsigned int ext[4];    // 扩展字段（4×32bit）
} BroadcastMessage;

// ========== 预置广播 ID ==========
EVENT_BROADCAST_ADDMSG      = 1   // 新消息通知（AItalk 用）
EVENT_BROADCAST_INPUT_METHOD = 2  // 输入法事件
EVENT_BROADCAST_POWER       = 3   // 电量更新
EVENT_BROADCAST_TIMERUPDATE  = 4   // 时间更新（msg 指向 struct tm）
EVENT_BROADCAST_SHUTDOWN    = 5   // 系统关机
```

**广播回调示例：**
```cpp
// 时间更新回调
static void on_time_update(BroadcastMessage* msg) {
    if (msg->msg && msg->msg_len == sizeof(struct tm)) {
        struct tm* t = (struct tm*)msg->msg;
        // CST = UTC+8
        int hour = t->tm_hour + 8;
        if (hour >= 24) hour -= 24;
        DateTimeView_main->setTime(hour, t->tm_min);
        DateTimeView_main->updateTime();
    }
}

// 电量更新回调
static void on_power_update(BroadcastMessage* msg) {
    int level = msg->ext[0];
    BatteryView_main->setBatteryLevel(level);
}

// 在 goldie_app_run() 中注册
evt->register_broadcast_recv(EVENT_BROADCAST_TIMERUPDATE, on_time_update);
evt->register_broadcast_recv(EVENT_BROADCAST_POWER, on_power_update);
```

### 5.4 WifiService（WiFi 服务）

```cpp
WifiService* wifi = (WifiService*)wait_service(WIFI_SERVICE_INDEX);

// ========== STA 模式（连接路由器） ==========
int enabled = wifi->svr_sta_isEnabled();
int connected = wifi->svr_sta_isConnected();
char* current_ssid = wifi->svr_sta_connection_name();

// 扫描 WiFi
int ap_count = wifi->svr_sta_get_ap_list(ApInfo_t* output_array);
// ApInfo_t 包含: ssid[33], passwd[65], rssi

// 连接 WiFi
wifi->svr_sta_add_account("MyWiFi", "password123");
wifi->svr_sta_try_connect();

// ========== SoftAP 模式（设备开热点，用于配网） ==========
wifi->svr_softap_config("xiaohe", "12345678");   // 配置热点
bool ap_enabled = wifi->svr_softap_isEnabled();
wifi->trigger_event(WIFI_EVENT_SOFTAP_ENABLE, NULL); // 启用热点

// ========== 回调注册 ==========
wifi->register_callback(wifi_status_callback);

static void wifi_status_callback(int status, void* data) {
    switch (status) {
        case WIFI_STATUS_STA_CONNECTED:
            // 已连接 WiFi — 更新 UI
            break;
        case WIFI_STATUS_STA_DISCONNECTED:
            // WiFi 已断开
            break;
        case WIFI_STATUS_STA_SCANDONE:
            // 扫描完成 — 填充 AP 列表
            break;
    }
}
```

**配网流程示例：**
```cpp
// 设备启动后检查 WiFi 连接状态
if (!wifi->svr_sta_isConnected()) {
    // 5 秒后仍未连接，进入配网模式
    goldie_msleep(5000);
    if (!wifi->svr_sta_isConnected()) {
        wifi->svr_softap_config("xiaohe", "12345678");
        wifi->trigger_event(WIFI_EVENT_SOFTAP_ENABLE, NULL);
        // 用户用手机连接 "xiaohe" 热点后进行配网
    }
}
```

### 5.5 SleSdpService（星闪无线服务）

星闪是中国自主研发的新一代近距离无线通信技术。GoldieOS 通过 SleSdpService 提供设备发现、配对、连接管理等功能。

```cpp
SleSdpService* sle = (SleSdpService*)wait_service(SLE_SDP_SERVICE_INDEX);

// ========== 基本控制 ==========
sle->svr_enable();   // 启用星闪
sle->svr_disable();  // 禁用星闪

// ========== 设备扫描（主机模式） ==========
sle->svr_start_scan();
sle->svr_stop_scan();

// 获取已配对设备
sle_connection_info_t devices[10];
uint32_t count = 10;
sle->svr_get_paired_devices(devices, &count);

// 主动连接
sle->svr_connect(&device_addr);

// ========== 设备广播（从机模式） ==========
sle->svr_start_announce();  // 开始广播，让其他设备发现自己
sle->svr_stop_announce();

// ========== 配对 ==========
int pair_code = sle->svr_s_generate_pair_code();  // 生成配对码

sle_pair_data pdata;
pdata.addr = target_addr;
pdata.pair_code = entered_code;
sle->trigger_event(SLE_EVENT_PAIR, &pdata);  // 触发配对

// ========== 模式管理 ==========
sle->svr_set_mode(0);   // 0=主机模式, 1=从机模式
uint8_t mode = sle->svr_get_mode();

// ========== 回调 ==========
sle->register_callback(sle_status_callback, 0);

static void sle_status_callback(SleStatusType status, void* data) {
    switch (status) {
        case SLE_STATUS_ENABLED:
            // 星闪已启用
            break;
        case SLE_STATUS_DEVICE_LIST_UPDATED:
            // 设备列表已更新 — 刷新 UI
            update_device_display();
            break;
        case SLE_STATUS_CONNECTION_LIST_UPDATED:
            // 连接状态变化
            update_signal_display();
            break;
        case SLE_STATUS_PAIRED_STATUS_UPDATED:
            // 配对状态更新
            break;
    }
}
```

### 5.6 SleWtpService（星闪对讲传输服务）

WTP (Wireless Transport Protocol) 是星闪的无线上层传输协议，专门用于对讲机等需要低延迟音频传输的场景。

```cpp
sleWtpService* wtp = (sleWtpService*)wait_service(SLE_WTP_SERVICE_INDEX);

// ========== 音频编解码 ==========
wtp->voice_codec_init();     // 初始化对讲语音编解码器
wtp->voice_codec_destroy();  // 销毁（退出时务必调用）

// ========== 数据收发 ==========
// 发送音频数据
int ret = wtp->write_data(
    const uint8_t* data,    // 音频数据缓冲区
    uint32_t len,            // 数据长度
    bool is_start,           // 是否是一个传输周期的起始包
    bool is_end              // 是否是最后一个包
);

// 接收音频数据
uint8_t recv_buf[1024];
uint16_t conn_id = 0;
int len = wtp->read_data(recv_buf, sizeof(recv_buf), &conn_id);
// conn_id 指示数据来自哪个设备

// ========== 通知（来电/呼叫提醒） ==========
wtp_notify_data notify;
if (wtp->read_notify(&notify) > 0) {
    if (notify.notify_id == 1) {
        // 收到呼叫通知 — 播放提示音
        audio->play_ring(AUDIO_RING_ID_ALARM, 1);
    }
}
wtp->clear_notify();  // 清除通知
```

**对讲机应用的全双工音频流程（Walkie-talkie App 的真实代码简化）：**
```cpp
static int combo_thread(void* arg) {
    short* pcm_buf = (short*)goldie_malloc(1280);

    while (running_flag) {
        if (is_recording) {
            // ===== 发送模式 =====
            int len = audio->audio_read(pcm_buf, 1280);
            if (len > 0) {
                // 立体声→单声道
                int mono_count = (len / sizeof(short)) / 2;
                for (int i = 0; i < mono_count; i++) {
                    pcm_buf[i] = pcm_buf[i * 2];
                }
                // 通过星闪发送
                wtp->write_data((uint8_t*)pcm_buf, mono_count * sizeof(short),
                                is_start_packet, is_end_packet);
            }
        } else {
            // ===== 接收模式 =====
            uint16_t conn_id;
            int len = wtp->read_data((uint8_t*)pcm_buf, 1280, &conn_id);
            if (len > 0) {
                // 单声道→立体声（复制到双通道）
                for (int i = len/2 - 1; i >= 0; i--) {
                    pcm_buf[i * 2 + 1] = pcm_buf[i];  // 右声道
                    pcm_buf[i * 2] = pcm_buf[i];      // 左声道
                }
                audio->audio_write(pcm_buf, len * 2);
            }
        }
        goldie_msleep(20);
    }

    goldie_free(pcm_buf);
    return 0;
}
```

### 5.7 SdCardService（SD 卡服务）

```cpp
SdCardService* sd = (SdCardService*)wait_service(SDCARD_SERVICE_INDEX);

bool has_card = sd->IsSdCardExists();
sd->mount_disk();    // 挂载
sd->unmount_disk();  // 卸载
```

注意：通常在 `goldie_app_run()` 开始时检查 SD 卡状态，若不存在则提示用户或退出。

### 5.8 AlarmService（闹钟服务）

闹钟数据持久化在 SD 卡上，由 AlarmService 统一管理。

```cpp
AlarmService* alarm = (AlarmService*)get_service(ALARM_SERVICE_INDEX);

alarm->init();  // 从 SD 卡加载闹钟数据

AlarmInfo info = {
    .enabled = true,
    .m_hour = 7,
    .m_min = 0,
    .weekdays = { true, true, true, true, true, false, false },  // 周一到周五
    .ring_index = 0,
};
int idx = alarm->add_alarm(&info);           // 添加（返回索引）
alarm->update_alarm(idx, &info);             // 更新
alarm->del_alarm(idx);                       // 删除

int count = alarm->get_alarm_list(alarm_list, 10);  // 获取全部闹钟
alarm->cancel_alarm();                                // 取消正在响的闹钟
int active = alarm->get_actived_alarm();              // 获取当前正在响的闹钟索引
```

### 5.9 AualgoService（音频算法服务/唤醒词检测）

这个服务运行唤醒词检测算法。当应用需要进行录音或播放时，**必须先暂停它**以避免冲突，退出时恢复。

```cpp
AualgoService* algo = (AualgoService*)get_service(AUALGO_SERVICE_INDEX);

if (algo) algo->puase();  // 暂停 → 应用做音频操作
// ... 录音/播放 ...
if (algo) algo->run();    // 恢复 → 应用退出
```

### 5.10 NTPService（时间同步服务）

```cpp
NTPService* ntp = (NTPService*)wait_service(NTP_SERVICE_INDEX);

ntp->sync_time();              // 触发一次网络时间同步
struct tm current_time;
ntp->get_time(&current_time);  // 获取当前时间
```

---

## 6. 驱动访问

驱动的获取方式与服务类似，但使用 `wait_drv()` / `get_drv()` 函数。

```cpp
// 驱动索引枚举
enum DRIVER_INDEX {
    I2C_DRV_INDEX=0, DISP_DRV_INDEX=1, FATFS_DISK_DRV_INDEX=2,
    I2S_DRV_INDEX=3, WLAN_DRV_INDEX=4, TOUCH_DRV_INDEX=5,
    KEYBOARD_DRV_INDEX=6, SPI1_DRV_INDEX=7,
    BATTERY_DRV_INDEX=11,  // ← 电池驱动（最常用）
    RTC_DRV_INDEX=13,       // ← RTC 时钟驱动
    PA_DRV_INDEX=14,        // ← 功放驱动
    FLASH_DRV_INDEX=15,     // ← 片内 Flash
    MAX_DRV_INDEX,
};

// 获取方式
BatDrv* bat = (BatDrv*)wait_drv(BATTERY_DRV_INDEX);             // 阻塞等待
void* drv = wait_drv_timeout_ms(index, 5000);                     // 带超时
void* drv = get_drv(index);                                       // 非阻塞
```

**电池驱动使用示例：**
```cpp
BatDrv* bat = (BatDrv*)wait_drv(BATTERY_DRV_INDEX);

int soc = bat->read_power();       // 电池电量百分比 (0-100)
int charging = bat->is_charging(); // 是否正在充电 (1=是, 0=否)

// 周期性地更新电池图标
BatteryView->setBatteryLevel(soc);
BatteryView->setCharging(charging == 1);
```

---

## 7. OS 抽象层 (OSAL)

OSAL 封装了底层操作系统的线程、锁、内存等功能，使上层代码可移植。

```cpp
#include "goldie_osal.h"

// ========== 线程操作 ==========
void* thread = goldie_thread_create(
    int (*handler)(void* data),  // 线程入口函数
    void* data,                  // 传递给线程的参数
    const char* name,            // 线程名称（调试用）
    unsigned int stack_size      // 栈大小（字节），通常是 0x1000 (4KB)
);

// 设置优先级 (0-31，数值越小优先级越高)
goldie_thread_set_priority(thread, 20);

// 销毁线程（确保线程已退出标志后再调用）
goldie_thread_destroy(thread);

// ========== C++ 线程封装 ==========
#include "goldie_thread.h"
Goldie_Thread* t = new Goldie_Thread(thread_func, NULL, 0x1000);
// 支持 lambda:
Goldie_Thread* t = new Goldie_Thread([](void* data) -> int {
    while (running) { goldie_msleep(100); }
    return 0;
}, NULL, 0x1000);
delete t;  // 析构函数自动停止和清理线程

// ========== 互斥锁 ==========
goldie_mutex mutex;
goldie_mutex_init(&mutex);
goldie_mutex_lock(&mutex);
// ... 临界区代码 ...
goldie_mutex_unlock(&mutex);
goldie_mutex_destroy(&mutex);

// ========== 信号量 ==========
goldie_sem sem;
goldie_sem_init(&sem);
goldie_sem_post(&sem);   // V 操作，释放
goldie_sem_wait(&sem);   // P 操作，等待（阻塞）
goldie_sem_destroy(&sem);

// ========== 基础操作 ==========
goldie_msleep(100);                   // 睡眠 100 毫秒
void* p = goldie_malloc(1024);        // 分配 1KB 内存
goldie_free(p);                       // 释放内存

// ========== 时间戳 ==========
goldie_timeval tv;
goldie_gettimeofday(&tv);
uint64_t ms = tv.tv_sec * 1000 + tv.tv_usec / 1000;
```

---

## 8. 文件 I/O 与 SD 卡

GoldieOS 使用 FatFs 文件系统，SD 卡挂载在 `0:/` 路径下。

```cpp
#include "ff.h"

// ========== 文件打开 ==========
FIL file;
FRESULT res;

// 只读打开
res = f_open(&file, "0:/music/song.mp3", FA_READ);

// 覆盖写入（创建新文件）
res = f_open(&file, "0:/rec.wav", FA_WRITE | FA_CREATE_ALWAYS);

// 追加写入
res = f_open(&file, "0:/log.txt", FA_WRITE | FA_OPEN_APPEND);

// 检查返回值
if (res != FR_OK) {
    printf("打开文件失败，错误码: %d\n", res);
}

// ========== 文件读写 ==========
UINT bytes_done;

// 读取
char buf[256];
f_read(&file, buf, sizeof(buf), &bytes_read);

// 写入
f_write(&file, data, data_size, &bytes_written);

// 移动读写位置
f_lseek(&file, 44);  // 跳到偏移 44 字节处（例如 WAV 文件的数据区）

// 获取文件大小
uint32_t size = f_size(&file);

// ========== 关闭文件（极其重要！） ==========
if (file.fs) {
    f_close(&file);
    memset(&file, 0, sizeof(FIL));   // ★ 必须清零句柄
}

// ========== 目录遍历 ==========
DIR dir;
FILINFO info;

f_opendir(&dir, "0:/music/");
while (f_readdir(&dir, &info) == FR_OK && info.fname[0] != 0) {
    if (!(info.fattrib & AM_DIR)) {
        // 这是一个文件
        printf("找到文件: %s, 大小: %lu\n", info.fname, info.fsize);
    }
}
f_closedir(&dir);
```

**WAV 文件头结构（录音应用示例）：**
```cpp
typedef struct {
    char chunk_id[4];         // "RIFF"
    uint32_t chunk_size;      // 文件总大小 - 8
    char format[4];           // "WAVE"
    char subchunk1_id[4];     // "fmt "
    uint32_t subchunk1_size;  // 16 (PCM)
    uint16_t audio_format;    // 1 (PCM)
    uint16_t num_channels;    // 1 (单声道)
    uint32_t sample_rate;     // 8000
    uint32_t byte_rate;       // sample_rate × channels × bits/8
    uint16_t block_align;     // channels × bits/8
    uint16_t bits_per_sample; // 16
    char subchunk2_id[4];     // "data"
    uint32_t subchunk2_size;  // 音频数据大小
} wav_header_t;

// 写入 WAV 文件头
wav_header_t header;
memset(&header, 0, sizeof(header));
memcpy(header.chunk_id, "RIFF", 4);
memcpy(header.format, "WAVE", 4);
memcpy(header.subchunk1_id, "fmt ", 4);
header.subchunk1_size = 16;
header.audio_format = 1;
header.num_channels = 1;
header.sample_rate = 8000;
header.bits_per_sample = 16;
header.byte_rate = 8000 * 1 * 2;
header.block_align = 1 * 2;
memcpy(header.subchunk2_id, "data", 4);
// data_size 在录音结束后回填
header.subchunk2_size = 0;
header.chunk_size = 36;

f_write(&file, &header, sizeof(header), &written);

// 录音结束后回填实际大小
f_lseek(&file, 0);
header.subchunk2_size = total_bytes;
header.chunk_size = 36 + total_bytes;
f_write(&file, &header, sizeof(header), &written);
```

---

## 9. AI 对话 SDK（ConvAI Bridge）

ConvAI Bridge 封装了云端 AI 对话 SDK，提供语音对话、情感识别、自定义角色配置等功能。

### 9.1 基本使用

```cpp
#include "convai_bridge.h"

// ========== 获取引擎 ==========
convai_engine_t engine = convai_bridge_get_engine();

// ========== 生命周期控制 ==========
convai_bridge_start();    // 启动 AI 对话会话
convai_bridge_stop();     // 停止 AI 对话会话
convai_bridge_restart();  // 重启（等同于 stop + start）

// ========== 状态查询 ==========
convai_status_e status = convai_bridge_get_status();
// 状态值：
//   0 = IDLE（空闲）
//   1 = LISTENING（聆听中）
//   2 = THINKING（思考中）
//   3 = ANSWERING（回答中）
//   4 = INTERRUPTED（被打断）
//   5 = ANSWER_FINISHED（回答完毕）

int is_speaking = convai_bridge_is_speaking();  // 是否正在说话（非 0=是）
```

### 9.2 回调注册

```cpp
// ========== 状态回调：对话状态变化时触发 ==========
convai_bridge_on_status([](convai_status_e status) {
    switch (status) {
        case CONVAI_STATUS_IDLE:
            update_ui_text("待机中...");
            break;
        case CONVAI_STATUS_LISTENING:
            update_ui_text("聆听中...");
            break;
        case CONVAI_STATUS_THINKING:
            update_ui_text("思考中...");
            break;
        case CONVAI_STATUS_ANSWERING:
            update_ui_text("回答中...");
            break;
        case CONVAI_STATUS_INTERRUPTED:
            update_ui_text("已打断");
            break;
    }
});

// ========== 事件回调：连接状态变化 ==========
convai_bridge_on_event([](convai_event_code_e event, const char* info) {
    switch (event) {
        case CONVAI_EVENT_CONNECTED:
            printf("AI 服务已连接\n");
            break;
        case CONVAI_EVENT_DISCONNECTED:
            printf("AI 服务已断开\n");
            break;
        case CONVAI_EVENT_FAILED:
            printf("AI 服务连接失败: %s\n", info);
            break;
    }
});

// ========== 消息回调：接收服务器原始 JSON 消息 ==========
convai_bridge_on_message([](const char* json_msg) {
    // 解析 JSON 获取情感/函数调用信息
    cJSON* root = cJSON_Parse(json_msg);
    if (root) {
        cJSON* type = cJSON_GetObjectItem(root, "type");
        if (type && strstr(type->valuestring, "function_call_arguments.done")) {
            // 解析 function_call 获取 emotion 信息
            cJSON* name = cJSON_GetObjectItem(root, "name");
            if (name && strcmp(name->valuestring, "emotion") == 0) {
                // 提取情感类型：neutral/happy/angry/sad/doubt
                // 驱动表情动画更新
            }
        }
        cJSON_Delete(root);
    }
});
```

### 9.3 AI 配置（角色、音色、个性）

```cpp
// ========== 设置启动配置（在 start() 之前调用） ==========
// config_json 是 JSON 格式的配置字符串，包含：
//   - system_messages: 系统提示词数组（角色 + 个性 + 关系）
//   - tts_config: TTS 配置（voice_type, provider_params 等）
//   - product_id/product_key/product_secret/device_name: 设备认证信息

const char* ai_config = R"({
    "system_messages": [
        {
            "role": "system",
            "content": "你是一个温柔治愈的AI助手。说话温柔，善解人意。"
        }
    ],
    "tts_config": {
        "provider": "minimax",
        "provider_params": {
            "audio": {
                "voice_type": "Chinese (Mandarin)_Warm_Girl"
            }
        }
    }
})";

convai_bridge_set_startup_config(ai_config);

// ========== 获取当前配置 ==========
const char* current_config = convai_bridge_get_startup_config();

// ========== 刷新配置到运行中的引擎 ==========
// convai_update() 可以热更新系统提示词等配置
convai_update(engine, new_config_json);
```

**Settings 应用中 AI 配置的实际示例（角色扮演 + 情感）：**
```cpp
// 个性和角色组合生成最终 system prompt
static const char* personalities[] = {
    "你是一个温柔治愈的AI，说话温柔，善解人意...",      // 温柔治愈
    "你是一个古灵精怪的AI，充满想象力，喜欢开玩笑...",   // 古灵精怪
    "你是一个聪明稳重的AI，逻辑清晰，知识渊博...",       // 聪明稳重
};

static const char* relationships[] = {
    "你是用户的暖心大姐姐，用温柔的语气和ta对话，给予关心和鼓励...",
    "你是用户的温柔小老师，用简单易懂的方式解释知识...",
};

// 组合生成 JSON
void apply_ai_settings() {
    char full_prompt[2048];
    snprintf(full_prompt, sizeof(full_prompt),
        "%s %s",
        personalities[selected_personality],
        relationships[selected_relationship]);

    // 生成完整 JSON 配置
    char config_json[4096];
    snprintf(config_json, sizeof(config_json),
        "{"
        "  \"system_messages\": [{\"role\":\"system\", \"content\":\"%s\"}],"
        "  \"tts_config\": {\"provider\":\"minimax\", \"provider_params\":"
        "    {\"audio\":{\"voice_type\":\"%s\"}}}"
        "}",
        full_prompt, voice_types[selected_voice]);

    convai_bridge_set_startup_config(config_json);

    // 如果引擎已经启动，需要热更新
    if (convai_bridge_get_status() != CONVAI_STATUS_IDLE) {
        convai_update(convai_bridge_get_engine(), config_json);
    }
}
```

### 9.4 AItalk 表情动画驱动模式（完整示例）

AItalk 和 Settings 的 AI 对话页都使用类似的表情动画系统：

```cpp
// 情感枚举
typedef enum {
    EMOTION_NEUTRAL = 0,
    EMOTION_HAPPY,
    EMOTION_ANGRY,
    EMOTION_SAD,
    EMOTION_DOUBT,
} EmotionType;

static EmotionType current_emotion = EMOTION_NEUTRAL;
static convai_status_e sdk_status = CONVAI_STATUS_IDLE;

// 从 message 回调中提取情感
static void on_ai_message(const char* json) {
    // 解析 function_call，提取 emotion
    // 设置 current_emotion
}

// 动画循环（在后台线程中运行，每 200ms 更新一次）
static void update_animation() {
    // 先获取当前对话状态，决定 play_type
    PlayType play_type;
    switch (sdk_status) {
        case CONVAI_STATUS_IDLE:
        case CONVAI_STATUS_INTERRUPTED:
        case CONVAI_STATUS_ANSWER_FINISHED:
            play_type = PLAY_SILENCE;  // 待机表情（偶尔眨眼）
            break;
        case CONVAI_STATUS_ANSWERING:
            play_type = PLAY_SPEAK;    // 说话表情（根据 emotion 做嘴型动画）
            break;
        case CONVAI_STATUS_LISTENING:
        case CONVAI_STATUS_THINKING:
            play_type = PLAY_SLEEP;    // 眯眼/闭眼动画
            break;
    }

    // 根据 play_type 和 current_emotion 更新精灵帧
    // 例如：
    //   PLAY_SPEAK + EMOTION_HAPPY → 笑眼 + 上下弹跳
    //   PLAY_SPEAK + EMOTION_ANGRY → 怒眼 + 左右切换
    //   PLAY_SILENCE → 正常眼 + 随机眨眼
    //   PLAY_SLEEP → 闭眼逐帧动画（3 帧循环）

    // 刷新显示器
    Window_main->flush(eye_x, eye_y, eye_w, eye_h);
}
```

---

## 10. 完整开发模式与示例

### 10.1 带后台录音线程的录音机应用

```cpp
#include "main_ui.h"
#include "ff.h"
extern "C" {
#include "goldie_osal.h"
#include "service_manager.h"
#include "app_manager.h"
}

// 配置常数
#define SAMPLE_RATE   8000
#define FRAME_MS      80
#define BUF_SIZE      (2 * 2 * FRAME_MS * SAMPLE_RATE / 1000)  // 2560

// 全局状态
static AudioService* audio;
static SdCardService* sdcard;
static AualgoService* algo;
static FIL record_file;
static volatile int running_flag = 0;
static volatile int is_recording = 0;
static volatile int recording_busy = 0;
static volatile int thread_exit_flag = 0;
static uint32_t total_bytes = 0;

// 录音线程
static int record_thread(void* arg) {
    short* buf = (short*)goldie_malloc(BUF_SIZE);
    while (running_flag) {
        if (is_recording) {
            recording_busy = 1;
            int read_len = audio->audio_read(buf, BUF_SIZE);
            if (read_len > 0) {
                // 立体声→单声道（取左声道）
                int mono = (read_len / sizeof(short)) / 2;
                for (int i = 0; i < mono; i++) buf[i] = buf[i * 2];

                UINT written;
                f_write(&record_file, buf, mono * 2, &written);
                total_bytes += written;
            }
        } else {
            recording_busy = 0;
            goldie_msleep(30);
        }
    }
    goldie_free(buf);
    thread_exit_flag = 1;
    return 0;
}

// 开始/停止录音
static void on_record_click(void*) {
    if (!is_recording) {
        if (!sdcard->IsSdCardExists()) return;

        f_open(&record_file, "0:/rec.wav", FA_WRITE | FA_CREATE_ALWAYS);

        // 写 WAV 头（占位）
        wav_header_t hdr = {0};
        memcpy(hdr.chunk_id, "RIFF", 4);
        memcpy(hdr.format, "WAVE", 4);
        memcpy(hdr.subchunk1_id, "fmt ", 4);
        hdr.subchunk1_size = 16; hdr.audio_format = 1;
        hdr.num_channels = 1; hdr.sample_rate = 8000;
        hdr.bits_per_sample = 16;
        hdr.byte_rate = 8000 * 2; hdr.block_align = 2;
        memcpy(hdr.subchunk2_id, "data", 4);
        hdr.subchunk2_size = 0; hdr.chunk_size = 36;
        UINT w;
        f_write(&record_file, &hdr, sizeof(hdr), &w);

        total_bytes = 0;
        audio->record_start();
        is_recording = true;
    } else {
        // 停止录音
        is_recording = false;
        while (recording_busy) goldie_msleep(50);  // 等待线程完成当前帧
        audio->record_stop();

        // 回填 WAV 文件头中的实际数据大小
        f_lseek(&record_file, 0);
        wav_header_t hdr;
        memcpy(hdr.chunk_id, "RIFF", 4);
        memcpy(hdr.format, "WAVE", 4);
        memcpy(hdr.subchunk1_id, "fmt ", 4);
        hdr.subchunk1_size = 16; hdr.audio_format = 1;
        hdr.num_channels = 1; hdr.sample_rate = 8000;
        hdr.bits_per_sample = 16;
        hdr.byte_rate = 8000 * 2; hdr.block_align = 2;
        memcpy(hdr.subchunk2_id, "data", 4);
        hdr.subchunk2_size = total_bytes;
        hdr.chunk_size = 36 + total_bytes;
        f_write(&record_file, &hdr, sizeof(hdr), &w);

        f_close(&record_file);
        memset(&record_file, 0, sizeof(FIL));
    }
}

// === 生命周期 ===
static void goldie_app_run(void) {
    audio = (AudioService*)wait_service(AUDIO_SERVICE_INDEX);
    sdcard = (SdCardService*)wait_service(SDCARD_SERVICE_INDEX);
    algo = (AualgoService*)get_service(AUALGO_SERVICE_INDEX);
    if (algo) algo->puase();  // 暂停唤醒词

    main_ui_init();
    Button_record->setOnClick(on_record_click);

    running_flag = 1;
    void* handle = goldie_thread_create(record_thread, NULL, "rec", 0x1000);
    goldie_thread_set_priority(handle, 20);
}

static void goldie_app_exit(void) {
    running_flag = 0;
    is_recording = 0;
    while (!thread_exit_flag) goldie_msleep(100);
    if (audio) { audio->record_stop(); audio->play_stop(); }
    if (record_file.fs) { f_close(&record_file); memset(&record_file, 0, sizeof(FIL)); }
    window_exit();
    if (algo) algo->run();
}

static void goldie_app_suspend(void) {
    is_recording = 0;
    window_suspend();
}

static void goldie_app_resume(void) {
    window_resume();
}

static void goldie_touch_event(int p, int x, int y) {
    if (Window_main) Window_main->handleEvent(p, x, y);
}

static void goldie_keyboard_event(int p, int k) {
    if ((k == SYSTEM_KEY_VALUE_BACK) && (p == 1))
        goldie_exit_app(&recorder);
}

static App_t recorder = {
    "录音", goldie_app_run, goldie_app_exit,
    goldie_app_suspend, goldie_app_resume,
    goldie_touch_event, goldie_keyboard_event,
    (uint16_t*)record_icon,
};

static void recorder_entry(void) { install_app(&recorder); }
GOLDIE_INIT_CALL_(recorder_entry);
```

### 10.2 编译时配置控制

通过宏控制功能开关：

```cpp
// 启用 Opus 编解码（编译时定义）
#ifdef USE_OPUS_CODEC
    #define FILE_NAME "0:/rec.c2"
    #include "tiny_codec_opus.h"
    encoder_t* enc = encoder_init();
#else
    #define FILE_NAME "0:/rec.wav"
#endif

// 平台相关代码
#ifdef SUPPORT_SLE
    SleSdpService* sle = (SleSdpService*)wait_service(SLE_SDP_SERVICE_INDEX);
#endif

#ifdef PLATFORM_TYPE_WS63
    // WS63 特有初始化
#endif

// 调试开关
#ifdef DUMP_PCM_FOR_DEBUG
    FIL dump_file;
    f_open(&dump_file, "0:/debug.pcm", FA_WRITE | FA_CREATE_ALWAYS);
#endif
```

### 10.3 消息队列模式（AItalk）

```cpp
#define MSG_QUEUE_SIZE 4

typedef struct {
    char uid[32];
    char content[1500];
} ChatMsg;

typedef struct {
    ChatMsg messages[MSG_QUEUE_SIZE];
    volatile int head, tail, count;
    goldie_mutex mutex;
} MsgQueue;

static MsgQueue msg_queue;

// 初始化
static void msg_queue_init() {
    msg_queue.head = 0;
    msg_queue.tail = 0;
    msg_queue.count = 0;
    goldie_mutex_init(&msg_queue.mutex);
}

// 生产者（来自广播回调）
static void on_new_msg(BroadcastMessage* msg) {
    goldie_mutex_lock(&msg_queue.mutex);
    if (msg_queue.count < MSG_QUEUE_SIZE) {
        ChatMsg* slot = &msg_queue.messages[msg_queue.tail];
        // 复制消息内容到 slot
        msg_queue.tail = (msg_queue.tail + 1) % MSG_QUEUE_SIZE;
        msg_queue.count++;
    }
    goldie_mutex_unlock(&msg_queue.mutex);
}

// 消费者（在 UI 线程中）
static bool msg_queue_pop(ChatMsg* out) {
    goldie_mutex_lock(&msg_queue.mutex);
    if (msg_queue.count == 0) {
        goldie_mutex_unlock(&msg_queue.mutex);
        return false;
    }
    *out = msg_queue.messages[msg_queue.head];
    msg_queue.head = (msg_queue.head + 1) % MSG_QUEUE_SIZE;
    msg_queue.count--;
    goldie_mutex_unlock(&msg_queue.mutex);
    return true;
}
```

---

## 11. 编码规范与最佳实践

### 11.1 必须遵守 (MUST)

| # | 规则 | 说明 |
|---|------|------|
| 1 | C 头文件用 `extern "C" {}` 包裹 | C++ 文件中引用 C 头文件时必须 |
| 2 | 退出时彻底清理线程 | 置 flag→等待 exit_flag→destroy |
| 3 | 音频操作前暂停 AualgoService | `algo->puase()`，退出时 `algo->run()` |
| 4 | 文件关闭后清零 | `f_close(); memset(&file,0,sizeof(FIL));` |
| 5 | 线程间共享数据加锁 | `goldie_mutex_lock/unlock` |
| 6 | 实现全部 6 个生命周期函数 | run/exit/suspend/resume/touch/keyboard |
| 7 | 处理 BACK 键 (300) | `pressure==1` 时退出应用 |
| 8 | 注册广播接收器 | 时间更新、电量变化等系统事件 |

### 11.2 严禁操作 (MUST NOT)

| # | 规则 | 说明 |
|---|------|------|
| 1 | 不手动修改 main_ui.h | 由 tinyui 工具自动生成 |
| 2 | 不在线程退出前 destroy | 先确保 exit_flag 为 1 |
| 3 | 不残留文件句柄 | 退出前全部 f_close |
| 4 | 不在主线程中阻塞 | run() 和事件处理函数中不长时间阻塞 |
| 5 | 不跳过 mutex unlock | 所有 return 路径都必须解锁 |

### 11.3 内存与栈管理

```
动态内存    : goldie_malloc() / goldie_free()
线程栈      : 0x1000 (4KB) — 标准
             0x2000 (8KB) — 重负载音频线程
音频缓冲区  : 录音 = 2 × channels × frame_ms × rate / 1000
             播放 = 2048 字节（典型值）
```

### 11.4 资源图片规范

```
格式       : RGB565 (16-bit color)
引入       : #include "rgb16_名称_W_H.h"
使用       : widget->setImageBuffer((uint16_t*)&rgb16_名称_W_H)
裁剪遮罩   : widget->setBoundaryBuffer(boundary_array)
Alpha 遮罩 : widget->setMaskBuffer(mask_array)
```

### 11.5 调试技巧

```cpp
// 使用 printf 输出调试信息
printf("[MyApp] status=%d, value=%d\r\n", status, value);

// 使用 DUMP_PCM_FOR_DEBUG 宏导出 PCM 数据
#ifdef DUMP_PCM_FOR_DEBUG
    FIL dump;
    f_open(&dump, "0:/debug.pcm", FA_WRITE | FA_CREATE_ALWAYS);
    f_write(&dump, pcm_data, len, &written);
    f_close(&dump);
    memset(&dump, 0, sizeof(FIL));
#endif

// 屏蔽未使用变量警告
unused(x);
```

---

## 12. 已有 App 参考索引

学习开发各类 App 时，可以参考现有实现：

| App | 目录 | 行数 | 展示的技术 |
|-----|------|------|-----------|
| **Launcher** | `apps/launcher/` | 491+138 | 应用列表管理、广播接收、自定义控件(Battery/WiFi)、ScrollView、DateTimeView |
| **Recorder** | `apps/recorder/` | 749+100 | 音频 I/O、多线程(录音+播放)、FatFs 文件操作、WAV/C2 格式、Opus 编解码、ProgressBar |
| **AItalk** | `apps/AItalk/` | 467+123 | AI 对话、表情动画系统、消息队列、广播机制 |
| **Settings** | `apps/settings/` | 1987+841 | 多页面架构、WiFi 管理、SLE 配对、AID 配置、JSON 解析、SpinnerView、自锁按钮 |
| **Alarm** | `apps/alarm/` | 718+344 | 闹钟CRUD、AlarmService、多页面、Checkbox、ListView、ImgButtonView |
| **Story** | `apps/Story/` | 410+200+649 | MP3 解码(Helix)、FatFs 目录遍历、线程+信号量、GBK转UTF-8 |
| **Walkie-talkie** | `apps/Walkie-talkie/` | 617+391 | SLE WTP 音频流、对讲全双工、SLE 回调、设备列表、PTT 按钮 |
| **DualScreen AI** | `apps/dualscreen_ai/` | 532+90 | WiFi 配网模式、SoftAP、双屏表情动画 |
| **Charging Only** | `apps/charging_only/` | 154+86 | 纯显示动画、简单线程 |
| **Animation Player** | `apps/animaton_player/` | 132+70 | 精灵动画播放、AudioService 响铃 |
| **Shut Down** | `apps/shut_down/` | 64+73 | 最简应用参考 |

---

> **文档版本**：v1.0  
> **适用版本**：GoldieOS (WS63 Platform)  
> **最后更新**：2026-07
