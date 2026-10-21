# P0 alarm mode

This fork adds an opt-in critical alert path to Gotify Android 2.10.1.

## Behavior

- Messages with priority 8 or higher use the `Critical alarm messages (≥8)` channel.
- When `Critical alarm` is enabled in notification settings, the app schedules an
  exact system alarm for one second after receipt.
- The alarm foreground service plays the device alarm tone for at most 30 seconds
  with `USAGE_ALARM`, holds a partial wake lock, and provides a `Stop alarm` action.
- The same P0 message is never replayed, and a new P0 alarm is suppressed for five
  minutes after the previous alarm.
- Large batches of missed messages trigger at most one alarm for the highest-priority
  message in the batch.

This version does not place calls, launch a full-screen activity, or bypass Do Not
Disturb. P0 events still need a Feishu copy when they are operationally critical.

## Build

The development variant installs beside the official app as `Gotify DEV`
(`com.github.gotify.dev`):

```powershell
.\gradlew.bat --no-daemon lintKotlin :app:testDevelopmentUnitTest :app:assembleDevelopment :app:lintDevelopment
```

The APK is written to `app/build/outputs/apk/development/app-development.apk`.

## Acceptance

1. Grant notification and exact-alarm permissions, then enable `Critical alarm`.
2. Put `Gotify DEV` in the background with the screen off.
3. Send a priority-8 message through a test application.
4. Verify one alarm starts, the notification exposes `Stop alarm`, the alarm ends
   after 30 seconds, and a second P0 inside five minutes does not ring again.

Do Not Disturb must allow alarms and the system alarm volume must be audible for
this path to be reliable.
