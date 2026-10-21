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
