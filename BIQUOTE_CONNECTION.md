# BiQuote Connection Engine — Stage 2

## Verified contract (documentation)
- REST snapshot: GET https://biquote.io/api/XAUUSD?allowStale=false
- SignalR hub: https://biquote.io/hubs/tick
- Client method: Subscribe(string[] symbols), called with ["XAUUSD"]
- Server event: ReceiveTick(Tick)
- Refresh method: GetLatestTick("XAUUSD"); REST snapshot is the fallback if hub invocation fails.
- Public read endpoints are documented as requiring no API key.
- SignalR Java client is WebSockets + JSON only; reconnect is implemented in this app because automatic reconnect is not provided by the Java client.

## Freshness policy
- Local no-tick threshold: 15 seconds -> STALE.
- A provider-marked stale quote, closed market, or quoteAgeSeconds > 300 -> STALE.
- A tick is not considered LIVE merely because an old price exists. The UI only displays prices after a valid response/tick.
- Receive timestamp is local epoch milliseconds. Source timestamp is parsed from ISO-8601 timestamp/time when available.

## Retry policy
1, 2, 4, 8, 16, 32, then 60 seconds maximum. One in-flight connect attempt is allowed.

## Important verification limitation
The endpoint and method names above are taken from BiQuote's published API documentation. This coding environment could not resolve biquote.io at runtime, so no real HTTP response, SignalR handshake, or ReceiveTick payload was captured during this implementation. Therefore no claim of live connection success is made. The first Android device / GitHub Actions validation must confirm the current server contract and record the actual payload. A failed hub call is not silently represented as LIVE.

## Dependencies
- com.microsoft.signalr:signalr:8.0.33
- com.google.code.gson:gson:2.13.2
- org.slf4j:slf4j-jdk14:1.7.36

## Project bootstrap choices
This repository had only README.md before Stage 2. A minimal Java Android app shell was added so the connection engine and REFRESH control have a real host. Namespace/applicationId: com.xauusdpro99. SDK: compile/target 35, min 23, Java 17. These are initial bootstrap choices and can be adjusted in a later explicit instruction.

## Not completed / not proven
- No captured live XAUUSD tick payload.
- No on-device network/airplane-mode/timeout test yet.
- No successful APK build until the GitHub Actions run completes.
