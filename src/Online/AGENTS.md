# Online package guidance

This package supplies the authoritative Java room server and the synchronous transport shared by desktop and Android. The browser counterpart is `web/src/online.js`.

## Authority and membership

- Keep authorization, expected-revision checks, turn checks and legal actions together under `RoomRegistry`'s lock. Derive turns from `GameState.currentPlayer()`; four-player turns are not index + 1. Revisions also change on membership events.
- Room codes are public invitations, not credentials. Generate bearer tokens with `SecureRandom`; send them only in `Authorization`. Never include tokens in URLs, logs, public `OnlineState`, saves or exports. Preserve defensive copies of sessions, names and history.
- Clients pin room identity/count, nondecreasing revisions, the seat once play starts, and terminal status. Reject malformed or inconsistent snapshots instead of applying them. Transient I/O failures retain membership for reconnect; explicit leave/close releases it.
- Preserve late-seat cleanup: cancelling an initial create/join/match must let its credential response finish, then release that captured client's seat. Fence callbacks from old room generations. Do not interrupt entry and lose its newly reserved credential, or let old cleanup close a newer client.

## Transport and resource bounds

- Keep `OnlineClient`/`UrlConnectionTransport` Android-compatible: standard `HttpURLConnection`, no desktop-only `java.net.http` or `Flow`. Call blocking methods off the UI thread. Check `android/app/build.gradle`'s shared-source allowlist when adding dependencies.
- Require public HTTPS; Java permits HTTP only for loopback/private numeric addresses. Preserve normal TLS verification, no redirects, bounded reads, disconnect/stream cleanup and cancellation. Never automatically retry an uncertain POST: the server may already have applied it.
- Preserve strict `Wire` parsing and limits: 2 KiB requests, 192 KiB responses, exact fields, valid UTF-8, bounded numeric values and at most 1,000 history entries. Do not surface raw server error bodies.
- Keep finite capacity/deadlines. Defaults are 128 rooms, 8 workers, 64 queued requests and 32 concurrent client requests; normal client operations allow 12 seconds and cold-start entry 90 seconds. Changes need tests for exhaustion, expiry and cancellation. JDK HTTP server `maxReqTime`/`maxRspTime` properties use **seconds**.

## HTTP framing and CORS

- Preserve deliberate `sun.net.httpserver.drainAmount=0`: rejecting a hostile nonempty body must not cause an unbounded drain.
- Keep `acknowledgeEmptyRequest` before CORS handling. Only when `Transfer-Encoding` is absent and `Content-Length` is absent or exactly one canonical `0`, read once and require EOF. The JDK already knows the length is zero, so this touches no frame payload and prevents premature keepalive closure. Do not extend this helper to nonempty/chunked bodies or replace its regression with client retries/reconnects.
- `QUORIDOR_ALLOWED_ORIGINS` is an explicit canonical-origin allowlist, empty by default. Keep exact matches, HTTPS or loopback HTTP origins, `Vary: Origin`, bounded GET/POST preflights and only Authorization/Content-Type headers. No wildcard, arbitrary reflection or cookie credentials. Allowed origins must still pass normal bearer authorization; health and error responses need the same CORS policy.

## Relevant checks

Run commands from the repository root; use owned loopback fixtures rather than public services.

- Java: `pwsh -File ./test.ps1 -Headless`. Focused sources are `tests/OnlineServerTest.java`, `tests/OnlineTransportTest.java`, `tests/OnlineCorsTest.java` and `tests/OnlineMatchControllerTest.java`. The CORS class is **`Online.OnlineCorsTest`**; retain its same-TCP `persistentEmptyRequests` regression.
- After the Java harness compiles tests, a focused Windows rerun is `java -ea -cp "out/classes;out/test-classes" Online.OnlineCorsTest` (use `:` as the classpath separator on Linux). Other relevant class names are `OnlineServerTest`, `OnlineTransportTest` and `Board.OnlineMatchControllerTest`.
- Web/protocol: `pwsh -File ./test-web.ps1 -SkipInstall` with pinned browser dependencies installed. It owns `tools/WebInteropServer.java` and exercises `web/tests/online.test.js` plus browser flows. Running that Node test alone skips real-server cases unless `QUORIDOR_TEST_SERVER` is supplied; do not report skipped cases as interoperability coverage.
- Shared Android transport changes: `pwsh -File ./test-android.ps1` with the documented SDK/emulator setup; native regressions include `android/app/src/androidTest/java/com/quoridor/mobile/OnlineInteropTest.java` and `LifecycleTest.java`.
