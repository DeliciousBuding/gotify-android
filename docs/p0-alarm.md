# Gotify Android P0 强提醒

本分支基于 Gotify Android 2.10.1，提供可关闭的 `priority=10` 系统闹铃路径。
开发包使用 `com.github.gotify.dev`，显示名 `Gotify DEV`，可与官方包并存。

## 告警语义

| Gotify priority | 行为 |
|---|---|
| `10` | 唯一会触发系统闹铃的值；使用 `USAGE_ALARM`，最长播放 30 秒 |
| `8–9` | 保持高优先级通知、振动和通知栏提示，不触发系统闹铃 |
| `0–7` | 使用 Gotify 原有通知渠道 |
| `>10` | 不触发系统闹铃 |

P0 事件仍必须同时走飞书主通道。Gotify 与主服务同在 jp1；凡是“jp1 失联”一类事件，不能只依赖 Gotify。

## 行为

- 设置中的 `强提醒` 默认关闭。开启后只对 `priority=10` 生效。
- 收到消息后通过 `setAlarmClock()` 安排 1 秒后的精确系统闹钟。
- 闹钟广播启动 `mediaPlayback` 前台服务，循环播放系统闹铃音，最长 30 秒。
- 前台服务持有 partial wake lock，并在通知栏提供 `停止闹铃`。
- 相同消息 ID 不重复触发；不同 P0 消息在 5 分钟内共享冷却。
- 只有实际触发 P0 的消息通知附带 `停止闹铃`；冷却中去重的 P0 消息仍保留高优先级通知，但不显示无效停止动作。
- 断线补收一批消息时，只挑选其中最新的 `priority=10` 消息触发一次；同批的 `priority=11` 不会压掉 `priority=10`。
- 精确闹钟不可用时，立即启动前台服务作为降级。
- 前台服务无法启动、没有可用铃声或 `MediaPlayer` 播放失败时，回退到有系统提示音的高优先级普通通知，不让后台调度异常直接崩溃。
- 状态栏消息通知与闹铃服务分离；P0 消息通知渠道本身静音，避免通知音和闹铃叠加。降级路径会改用正常高优先级渠道。

本版本不拨打电话、不强制全屏、不绕过勿扰。

## 设置与权限

设置页新增 P0 分类，显示并引导处理以下状态：

- 通知权限；
- 精确闹钟权限；
- 电池优化；
- 本机测试强提醒（不向服务器发送消息，走与真实消息相同的精确闹钟广播和前台服务链路）；
- 勿扰、闹铃音量和后台保活限制说明。

Android 14+ 新安装且 target 33+ 的应用默认没有 `SCHEDULE_EXACT_ALARM`。应用通过
`AlarmManager.canScheduleExactAlarms()` 判断状态，并提供系统设置入口。未授权时仍尝试立即播放，
但这不是精确闹钟路径。

精确闹钟权限是按需授权项：应用启动不会因为它未授予而阻断登录。用户启用强提醒或点击权限项时才会进入
系统授权页；WebSocket 重连在权限缺失时自动退回 Handler 调度。

Android 15+ 禁止从 `BOOT_COMPLETED` 启动 `mediaPlayback` 前台服务。本实现只从精确闹钟广播或用户点击测试启动，
不使用该禁止路径。

勿扰模式必须允许闹钟，且系统闹铃音量必须可听见。`USAGE_ALARM` 不会无条件绕过勿扰。
WebSocket 前台服务被 OEM 电池策略杀掉时，手机收不到消息，后续强提醒也无从触发；这是连接层限制，
不是闹铃服务能够补回的路径。

## Android 规范与兼容边界

实现遵循 Android 公开 API，而不是尝试修改系统音量、抢占音频焦点或绕过勿扰：

- 精确唤醒使用 `AlarmManager.setAlarmClock()`，调用前通过
  `canScheduleExactAlarms()` 检查用户授权；未授权时立即启动前台服务降级。精确闹钟是 Android
  从后台启动媒体播放前台服务时明确认可的豁免路径，精确闹钟不会受一般后台启动限制影响。
- 播放服务声明 `mediaPlayback` 前台服务类型和
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限，停止时由系统随前台服务结束移除持续通知。
- 铃声使用 `AudioAttributes.USAGE_ALARM` 和 `CONTENT_TYPE_SONIFICATION`，与 Home Assistant
  的 `alarm_stream`、ntfy 的持续高优先级告警采用同一 Android 音频路径。它通常能在震动模式发声，
  但系统勿扰仍可禁止闹钟，应用不会也不应绕过该选择。
- P0 消息使用独立静音高优先级渠道，铃声只由 `MediaPlayer` 输出，避免通知提示音与循环铃声叠加。
  这是 ntfy #1879 记录的“双音/回声”问题所需的拆分。
- 不使用全屏 `Intent`、悬浮窗、拨号或强行调高音量。Android 仅允许来电、用户明确设置的闹钟等少数
  场景使用全屏通知；P0 服务器消息不冒充这些场景。
- `SCHEDULE_EXACT_ALARM` 是用户可授予、可撤销的权限，不申请自动授予且仅限闹钟/日历核心用途的
  `USE_EXACT_ALARM`。若未来发布到 Google Play，需要在 Play Console 如实申报精确闹钟用途。

取消链路包含两层：

1. P0 消息通知只在本次确实触发强提醒时显示“停止闹铃”。按钮通过不可变的
   `PendingIntent` 通知 `P0AlarmService`；服务先取消尚未触发的精确闹钟，再 `stopSelf()`。
2. `onDestroy()` 停止并释放 `MediaPlayer`、释放 partial wake lock；系统结束
   `mediaPlayback` 前台服务时同步移除持续通知。定时 30 秒结束与用户主动停止走同一清理路径。

MuMu Android 12 / API 32 实测 message 46：点击“停止闹铃”后，系统音频记录从
`MediaPlayer ... usage=USAGE_ALARM state:started` 变为 `state:stopped` 并立即
`releasing player`；P0 前台服务消失，消息本身仍保留为普通可查看通知。

## 竖屏验收

MuMu 在 `720×1280`、180 dpi 的竖屏下完成三处实看：

- 消息列表保留左侧发送者图标、标题/时间/删除动作，正文在单行后省略并可点开查看，没有横向溢出。
- 设置页的 P0 分类使用标准 Preference 行，标题与摘要允许两行；滚动后“可靠性限制”和后续“连接”
  分类完整可达，没有截断或重叠。
- 通知栏用单列布局显示 P0 标题、摘要和“停止闹铃”；折叠态正文以省略号收口，停止动作无需展开即可操作。

同类实现可参考：

- [Gotify #216 Critical Alert](https://github.com/gotify/android/issues/216)：最早汇总 Home Assistant
  闹钟音频流方案的 Gotify 需求。
- [Gotify #203 Notification Sound until confirmation](https://github.com/gotify/android/issues/203)：
  持续响铃直到确认的原始需求。
- [Home Assistant Critical notifications](https://companion.home-assistant.io/docs/notifications/critical-notifications/)：
  Android alarm stream、高优先级与勿扰渠道的公开说明。
- [ntfy #189](https://github.com/binwiederhier/ntfy-android/issues/189)：持续铃声必须在通知被
  清除时显式停止；本实现把停止动作直接绑定到前台服务，避免该失效组合。
- [ntfy #1879](https://github.com/binwiederhier/ntfy/issues/1879)：通知渠道声音与循环播放器同时播放，
  会产生叠加/回声；本实现使用静音 P0 渠道规避。

## 简体中文

`values-zh-rCN` 覆盖登录、OIDC/证书、消息列表、导航、设置、日志、分享、通知渠道和 P0 强提醒。
存储用 preference key 与 enum value 标记为 `translatable="false"`，显示标签单独翻译，避免切换语言后配置失效。

构建启用 Android 13+ 的 per-app language：

```kotlin
androidResources {
    generateLocaleConfig = true
}
```

生成结果包含 `en-US` 和 `zh-CN`。简体中文可随系统语言生效，也可在 Android 系统设置的单应用语言中选择。

## 构建

```powershell
.\gradlew.bat --no-daemon :app:formatKotlinMain lintKotlin `
  :app:testDevelopmentUnitTest :app:assembleDevelopment :app:lintDevelopment
```

APK：

```text
app/build/outputs/apk/development/app-development.apk
```

## 验收

1. 安装 `Gotify DEV`，授予通知与精确闹钟权限，将电池优化设为不受限制。
2. 打开 `强提醒`，点击 `测试强提醒`，确认闹铃、停止按钮和 30 秒自动停止。
3. 让应用进入后台并熄屏，发送 `priority=9`，确认不会触发系统闹铃。
4. 发送 `priority=10`，确认精确触发系统闹铃，通知栏可停止。
5. 5 分钟内再发另一条 `priority=10`，确认冷却；超过 5 分钟后确认可再次触发。
6. 开启勿扰并分别测试“允许闹钟”和“禁止闹钟”，确认结果符合系统策略。
7. 检查飞书 P0 副本独立可达，不能把 Gotify 接收当作飞书投递成功的替代。
