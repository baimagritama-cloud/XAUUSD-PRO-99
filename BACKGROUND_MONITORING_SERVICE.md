# Stage 13 — Background Monitoring Service

## Implemented architecture
- User-started Android Foreground Service `MarketMonitoringService` using manifest foreground type `specialUse`, with a persistent low-importance notification.
- Service owns one BiQuote SignalR connection and one multi-timeframe OHLC engine while active; the Activity's foreground connection is closed when background mode is enabled to avoid duplicate streams.
- Service runs indicator, market-structure, Supply/Demand, candle-pattern and signal-scoring pipelines from closed candle snapshots, then routes EMA, zone touch, breakout, retest, rejection, BOS/CHOCH and confirmed BUY/SELL alerts through existing notification preferences and history.
- Live XAUUSD quote updates the persistent notification. SignalR reconnect/backoff remains active; ConnectivityManager callbacks log network loss and force reconnect when connectivity returns.
- OHLC refresh cadence: every 2 minutes. This is intentionally not a per-second OHLC polling loop.
- Economic calendar: provider polling every 15 minutes when endpoint and encrypted API key are configured; release/45m/30m/Actual alert windows are checked every 30 seconds while service is alive. Calendar data remains unavailable if not configured.
- WorkManager unique periodic maintenance check (minimum 15 minutes, connected-network constraint) records heartbeat status. It does not attempt to start a Foreground Service from the background.
- Boot/package-replaced receiver schedules WorkManager health check only. It does not auto-start continuous live monitoring after reboot.
- Service state, connection state, tick timestamp, WorkManager result and bounded reconnect log are persisted in SharedPreferences for the in-app status panel.

## Permissions
- INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS
- FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE
- RECEIVE_BOOT_COMPLETED
- Android 13+ runtime notification permission is requested by Activity. If denied, foreground service may still run but its notification visibility is restricted by Android; in-app history remains available.
- No exact AlarmManager permission is requested: WorkManager is sufficient for best-effort maintenance. Exact release-time alerts depend on the live Foreground Service and configured calendar source.
- Battery optimization is not bypassed automatically. User can open Android's battery optimization settings manually. Exemption is not a guarantee against OEM/system process management.

## Android limitations
- User must explicitly start monitoring while Activity is visible; background-start restrictions are respected.
- Force Stop stops the service and prevents automatic restart until the user opens the app again.
- Reboot does not silently start the live market service. The receiver schedules a WorkManager health check and records that the user must reopen/start monitoring.
- START_STICKY requests service recreation after some process deaths, but Android/OEM may still defer or suppress it. No always-on guarantee.
- WorkManager periodic work is inexact (15-minute minimum) and is not suitable for precise 45/30-minute announcements by itself.
- OEM battery savers, Doze, no network, provider outages, BiQuote stale/closed market, notification permission and Android task restrictions can interrupt monitoring.
- `specialUse` foreground service requires platform/Play policy compliance and appropriate review/disclosure when distributing through Play.
- Background execution has been compile/unit-test checked in CI, not physically validated on device across Doze, reboot, Force Stop, Android 13/14/15 or OEM battery savers.

## Validation status
- CI: `:app:testDebugUnitTest :app:assembleDebug`.
- Pure heartbeat policy tests cover fresh, stale, disabled and future heartbeat.
- Real device validation still required: permission prompt, persistent notification, lock screen/background operation, network loss/restore, battery saver, reboot, Force Stop and notification delivery.
