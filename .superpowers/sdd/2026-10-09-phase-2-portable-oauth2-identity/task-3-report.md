# Phase 2, Task 3 report: refreshable identity in OAuth2 sessions

## Session schema and migration behavior

`AuthSessionStore.AuthUser` now persists subject, display name, access token, refresh token, token expiry, the complete `IdentityContext`, and `identityFetchedAt` in the existing Redis JSON value. Creation writes the token family and identity together; refresh updates the same value atomically only after userinfo has been fetched and mapped. `userId()` and `name()` remain compatibility accessors. `toString()` redacts both credentials and summarizes identity without dumping claims.

`resolve()` treats malformed JSON and payloads missing expiry, identity, or identity timestamp as absent (the existing caller returns 401). It also rejects stored subject/display-name mismatches between the session and its identity. Redis JSON persistence and the existing session key/TTL remain unchanged.

## Refresh and single-flight state machine

- Return the resolved session only when the access token is outside the existing 30-second refresh skew and identity was fetched less than five minutes ago; a future timestamp is not fresh.
- Otherwise acquire the existing per-app-session Redis lock. The winner re-reads Redis after lock acquisition, so a request that started with an old snapshot cannot use a rotated refresh token if a previous winner has already persisted a ready session.
- If the token is near expiry, refresh first, retain the old refresh token when the provider omits a replacement, then fetch identity with the new access token. If only identity is stale, fetch with the current access token and leave the token family unchanged.
- Persist the complete winner record only after identity mapping succeeds. Provider, mapping, invalid-token, and persistence errors do not return the prior stale identity. Authentication errors map to 401; provider/invalid-claim errors retain safe 502 handlers.
- A lock loser polls the stored session and returns only a ready, persisted winner. It never returns its original snapshot or an expired/near-expiry token. A vanished session returns 401; a lock that disappears without a ready winner fails closed with a safe 502.
- The Redis lock lasts 65 seconds, covering the configured 15-second connect and 15-second response limits for the sequential token and userinfo requests; losers poll for up to 70 seconds. On success the lock is allowed to expire, preventing requests that read the old session before the write from racing a second rotated-token refresh. On failure it is released for retry.

## RED/GREEN evidence

- Session-shape RED: `mvn -pl agent-server -Dtest=AuthSessionStoreTest test` initially ran 10 tests with 2 expected failures: pre-migration payloads still resolved, and stored JSON lacked identity/timestamp.
- Resolver behavior RED: with the legacy refresh-only resolver semantics temporarily restored, `mvn -pl agent-server -Dtest=CurrentUserArgumentResolverTest test` ran 13 tests with 5 expected failures. The failures showed stale admin privileges surviving, stale provider/mapping failures returning old identity, and real concurrent requests diverging instead of converging on the rotated token and winner identity.
- Focused GREEN: `mvn -pl agent-server -Dtest='AuthSessionStoreTest,OAuth2ClientTest,CurrentUserArgumentResolverTest,AuthControllerTest' test` passed: 57 tests, 0 failures, 0 errors.
- Full server GREEN: `mvn -pl agent-server test` passed: 217 tests, 0 failures, 0 errors.
- Final focused and full runs were repeated after the resolver's post-lock Redis re-read was added; both passed with the same counts. `git diff --check` passed.

## Files and call sites

- `AuthSessionStore`: identity-bearing record, atomic JSON persistence, migration rejection, and redacted logging/string form.
- `OAuth2Client` and `JsonPointerOAuth2IdentityMapper`: `fetchIdentity(accessToken)` decodes the provider's raw JSON and delegates all claim interpretation to the configurable mapper; transport/decode/provider failures are sanitized. The old hard-coded userinfo claims path was removed.
- `AuthController`: callback persists mapped identity and timestamp with tokens; `/api/auth/me` exposes subject/display name, tenant, primary organization, roles, organization scopes, and admin flags, never provider credentials. Callback/logout logs remain best-effort and sanitized; public paths and OAuth state/PKCE flow are unchanged.
- `CurrentUserArgumentResolver`: five-minute freshness and single-flight refresh/convergence behavior described above.
- `ChatController`: both chat execution paths pass stored `IdentityContext` directly to the orchestrator instead of rebuilding it from legacy role text.
- Added `TestAuthUsers` for consistent identity-bearing fixtures; updated auth, resolver, and session tests plus affected controller/knowledge test fixtures for the new `AuthUser` shape.

## Security and race self-review

- Refreshed identities are used for the request only after their persisted update completes. Stale roles are not used as fallback on provider or mapper failure.
- The winner re-reads the session after obtaining the lock; this closes the stale-snapshot/rotated-refresh-token replay race. Losers re-resolve Redis on each poll and can return only the stored ready winner.
- The successful lock is retained for its bounded lease to prevent a pre-write reader from acquiring a just-freed lock and replaying the old refresh token. The lease exceeds the configured OAuth request timeouts; its unsuccessful path releases it. Lock timeout and missing-session outcomes fail closed.
- The callback, logout, resolver, and session store no longer log provider exception details or payloads. `AuthUser.toString()` and `TokenResult.toString()` redact credentials. `/api/auth/me` is tested not to expose tokens.
- Authorization and agent execution consume `IdentityContext`; the compatibility `userType()` projection is not used to reconstruct execution identity.

## Concerns

- The full suite passes but emits existing Java/Mockito/Netty warnings and Reactor `IllegalMonitorStateException` worker logs from the unrelated BlockHound/client tests; these did not fail the suite.
- The real Redis concurrency test uses the repository's existing local integration-test Redis configuration and a random app-session key, cleaning up its session and lock afterward.
- No other task-specific concern found.

## Fix Round 1

### RED/GREEN

- RED: added real-Redis regressions for token rotation followed by identity-provider failure and by session-write failure. Before the fix, `mvn -pl agent-server -Dtest=CurrentUserArgumentResolverTest test` ran 15 tests with 2 expected failures: the stale session remained after a rotated refresh token, and the old owner deleted a simulated successor lock.
- Poll RED: `mvn -pl agent-server -Dtest='CurrentUserArgumentResolverTest#slowRedisReadsDoNotOverflowWinnerPolling' test` reproduced `Flux.interval`'s `OverflowException` when a Redis read took longer than the 100ms tick; after switching to demand-driven `repeatWhenEmpty`, that test passed.
- GREEN: the final resolver class run passed 21 tests, 0 failures/errors. This includes invalidation/retry, successor-lock ownership, post-lock reread cleanup, stale-snapshot recheck, non-rotating-provider, virtual-time timeout, and slow-poll tests.
- Required focused suite: `mvn -pl agent-server -Dtest='AuthSessionStoreTest,OAuth2ClientTest,CurrentUserArgumentResolverTest,AuthControllerTest' test` passed 65 tests, 0 failures/errors.
- Full server suite: `mvn -pl agent-server test` passed 225 tests, 0 failures/errors. `git diff --check` passed.

### Lock algorithm and timing proof

This round supersedes the earlier 65-second lease description above. The existing per-app-session lock key is retained, but its value is now a fresh UUID owner ID, never an access or refresh token. Release runs one Redis Lua compare-and-delete: it deletes the key only if its current value still matches that owner's UUID. The script is used on success, failure, acquisition timeout, and post-lock reread failure, so an old owner cannot remove a successor's lock.

The time bounds are explicit:

| Phase | Bound |
|---|---:|
| SETNX response | 5 s |
| Lock-owned reread, refresh, userinfo, and complete session write | 65 s |
| Session invalidation on a failed rotated refresh | 10 s |
| Compare-and-delete cleanup | 10 s |
| Lock lease | 120 s |

OAuth refresh and userinfo each have 15-second connect and 15-second response limits, so their sequential transport budget is at most 60 seconds; the 65-second owner-operation timeout includes five seconds of application/Redis overhead. Conservatively adding acquisition, owner operation, and both cleanup budgets gives 90 seconds, strictly below the 120-second lease with 30 seconds of margin. The loser polls for up to 90 seconds, longer than the 85-second owner-operation-plus-cleanup budget, and only returns a fresh persisted winner. Polling is demand-driven: each next Redis check starts only after the prior check completes and a 100ms delay, so slow Redis reads cannot overflow an independently ticking interval. A timeout or disappeared lock without ready session fails closed.

### Rotation failure and cleanup behavior

After a successful token response, the resolver records whether a nonblank, changed refresh token was returned. If userinfo mapping/provider access or the complete Redis session write then fails before the new record is persisted, it deletes the old app session before compare-deleting the lock. A later request therefore resolves absent and returns 401 instead of replaying the consumed refresh token. A provider that omits `refresh_token` (or returns the unchanged value) retains the prior session and can retry with its existing refresh token.

If invalidation itself errors or exceeds its 10-second bound, cleanup does not release the lock; the request fails closed and the owner's bounded lease remains. Errors during the post-lock Redis reread are also inside the same owner-scoped cleanup wrapper, so they do not strand a live lease intentionally.

### Self-review and commit

- Two concurrent requests that both read the old session either wait on the current owner and re-read the persisted winner, or, if they acquire after a prior owner has released, re-read Redis before using their stale snapshot. Neither path can replay the old refresh token or return stale privileges. The real-Redis concurrency test also verifies that successful owner cleanup deletes its own lock; the simulated successor-owner test verifies it cannot delete another owner's value.
- Compare-and-delete protects successor owners even if an earlier owner completes cleanup late. Owner values are opaque UUIDs and the integration test verifies they contain no token values.
- Credential/provider details remain absent from logs and user-facing errors.
- The fix was committed separately as `fix: make OAuth2 refresh lock ownership-safe`.
- No additional concerns found beyond the existing Redis availability assumption: if Redis itself cannot perform session invalidation, the resolver deliberately retains the lock until its bounded lease expires and does not return the stale session.

## Fix Round 2

This section supersedes Round 1's post-refresh invalidation behavior. Session invalidation now precedes every access-token refresh, rather than depending on cleanup after a provider returns a rotated token. A failed or cancelled refresh therefore cannot make an old, possibly consumed refresh token usable again.

### Refresh state machine and invalidation boundary

1. A stale caller acquires the existing per-app-session Redis lock and re-reads Redis while holding it.
2. If the access token is near expiry, `AuthSessionStore.delete(appToken)` must complete within 10 seconds before `oauth2.refresh` is even constructed/subscribed (`Mono.defer`). If Redis rejects or times out the invalidation, refresh is never invoked; the old refresh token remains unconsumed and a later request may retry.
3. After successful deletion, the old session remains absent while token refresh, userinfo fetch/mapping, and the complete new session write run. Any provider/mapping/write error, owner timeout, or subscriber cancellation can require login again, but cannot restore the old token family.
4. On success, a nonblank replacement refresh token is persisted. If the provider omits it or returns the same value, the old refresh token is carried into the new session. The session write still occurs only after identity mapping succeeds. A stale-identity-only refresh does not delete the session because it does not consume a refresh token; it still fails closed for that request if userinfo/mapping fails.
5. A request whose initial session read is empty, or a lock loser whose pre-lock snapshot is now absent, polls while the per-session lock exists. It returns only a persisted ready winner. An absent session with no live lock resolves unauthorized; a stale session with no lock fails closed as unavailable. No branch falls back to its pre-lock snapshot.

### Lock algorithm and timing proof

The lock keeps Round 1's opaque UUID value and atomic Lua compare-and-delete. It contains no access/refresh credentials; cleanup removes it only if Redis still has that owner's UUID. The owner wrapper still includes the post-lock session reread, so a reread error runs the same owner-scoped cleanup. Invalidation errors also take that cleanup path; subscriber cancellation may skip `onErrorResume`, but the session was already deleted and the lock naturally expires. A late old-owner cleanup cannot delete a successor lock because the Lua script compares values atomically.

| Bound | Maximum |
|---|---:|
| SETNX/acquire response | 5 s |
| Pre-refresh session invalidation (inside owner operation) | 10 s |
| Lock-owned invalidation, OAuth refresh, userinfo, mapping, and persistence operation | 75 s |
| Owner compare-and-delete cleanup | 10 s |
| Opaque lock lease | 120 s |
| Loser elapsed wait cap | 105 s |

The 75-second owner timeout is the end-to-end cap. It includes at most 10 seconds for pre-delete, the configured sequential OAuth refresh and userinfo request limits (each 15 seconds connect plus 15 seconds response), and up to five seconds for mapping/session persistence/scheduling overhead; anything slower times out and remains fail-closed. Conservatively adding the 5-second acquisition, 75-second owner operation, and 10-second cleanup gives 90 seconds, 30 seconds below the 120-second lease. Thus an ordinary owner cannot still be in its bounded cleanup when a successor acquires an expired lease. Even if cleanup is delayed until after expiry, compare-and-delete preserves the successor's distinct value. The 105-second loser cap is longer than the 90-second owner lifecycle budget, shorter than the 120-second lease, and wraps the whole demand-driven 100ms polling flow (including stalled Redis reads); the 1,000-poll count is a secondary bound, not the only timeout.

### RED/GREEN evidence

- RED command: `mvn -pl agent-server -Dtest='CurrentUserArgumentResolverTest#refreshNeverStartsWhenPreRefreshSessionInvalidationFails+cancellationAfterRotatedRefreshCannotRestoreTheOldSessionAfterLockExpiry+loserConvergesWhenSessionIsTemporarilyAbsentDuringWinnerRefresh+loserWaitHasAnElapsedTimeoutEvenWhenRedisReadStalls+refreshesAndPersistsWhenTokenNearExpiry+keepsExistingRefreshTokenWhenProviderOmitsANewOne' test` ran 6 tests with 6 expected failures. It showed refresh ran despite a failed delete, cancellation left the old Redis session usable, successful refresh lacked the required delete ordering, a concurrent reader observed a still-present old session instead of the in-progress absence, and the old waiter returned 401 instead of waiting/bounding a stalled read.
- The first implementation candidate left 1 of those 6 assertions failing: `oauth2.refresh(...)` was invoked while assembling the Reactor chain even though subscription followed the delete. Deferring method invocation itself with `Mono.defer` made the invalidation-failure test prove zero provider calls.
- GREEN resolver: `mvn -pl agent-server -Dtest=CurrentUserArgumentResolverTest test` passed 26 tests, 0 failures/errors.
- Required focused suite: `mvn -pl agent-server -Dtest='AuthSessionStoreTest,OAuth2ClientTest,CurrentUserArgumentResolverTest,AuthControllerTest' test` passed 70 tests, 0 failures/errors.
- Full server suite: `mvn -pl agent-server test` passed 230 tests, 0 failures/errors. `git diff --check` passed.

The invalidation regression now fails its first request on a simulated Redis delete error, verifies `oauth2` has no interactions, then retries after Redis recovers and successfully uses the same old refresh token—evidence that it was not consumed by the rejected attempt. The cancellation regression pauses after the rotated provider response at userinfo, confirms the Redis session is absent, disposes the subscriber, simulates lock expiry, and verifies the session remains absent and a later request gets 401 without a second refresh. The real-Redis concurrency regression starts a second request only after the owner's delete has made the key absent, then releases the controlled provider response and verifies both callers receive the one persisted winner with removed admin role. Successful refresh coverage verifies both rotating and non-rotating responses persist the proper refresh token (omitted and unchanged cases), and the rotating case checks delete → refresh → userinfo → update order.

### Self-review and commit

- No failure/cancellation path after provider refresh begins restores an old session; if the Redis delete is not acknowledged, provider refresh is not invoked. Delete timeout/errors remain within the owner wrapper, whose compare-and-delete cleanup also covers post-lock reread errors.
- Lock values remain opaque random UUIDs. Provider error text and credentials are not included in the new timeout/error messages or logs. Successful results still require mapped identity persistence before either owner or loser returns.
- The 105-second real `Mono.timeout` is tested with virtual time against a never-completing Redis reread, so waiting cannot become unbounded even when a read stalls.
- Separately committed as `67c0278` (`fix: invalidate OAuth2 session before refresh`).
- No additional task-specific concern found; successful Redis delete acknowledgment is the session store's invalidation boundary, and a failed/ambiguous delete never starts the provider call.

## Fix Round 3

### Logout-wins boundary

Logout now atomically overwrites the existing `authsession:<appToken>` Redis value with the credential-free `__AUTH_SESSION_LOGGED_OUT_V1__` sentinel and applies the same 30-day TTL as a live session. `resolve()` recognizes the sentinel as absent; refresh `update()` is a one-key Lua operation that checks for the sentinel and refuses to write over it. The tombstone expires naturally after the app-session lifetime, so there is no process-local state or unbounded Redis key retention.

The tombstone and guarded update both operate on the existing session key. This gives a single-key atomic boundary that is also compatible with Redis Cluster hash-slot rules, without changing the live-session key or JSON schema. The ordering is linearizable: if refresh writes first, logout replaces that value with the tombstone; if logout writes first, refresh sees the tombstone and fails closed with 401. A successful logout therefore cannot be followed by recreation from an earlier refresh, including when userinfo completes after logout.

`AuthController.logout()` now requires the local tombstone operation to complete before returning 204 and before best-effort provider revocation. If the session is already absent (such as during pre-refresh invalidation), logout still writes the tombstone before returning 204. When a current session is available, provider revocation remains best effort; its failure does not undo local logout. Redis failure/ambiguous acknowledgment does not produce a successful logout response.

### RED/GREEN evidence

- Concurrency RED: `mvn -pl agent-server -Dtest='AuthControllerTest#logoutWinsOverAnInFlightRefreshAndPreventsSessionRecreation' test` first failed against the previous implementation: after logout returned 204 and the blocked refresh was released, `resolve(appToken)` returned the newly persisted `AuthUser` instead of absent.
- Single-key placement RED: after adding assertions that the sentinel occupy the original session key, the same test failed on the two-key tombstone candidate (`expected __AUTH_SESSION_LOGGED_OUT_V1__, actual null`). This prompted using a single-key tombstone/update script rather than two Redis keys.
- GREEN race: the same command passed: 1 test, 0 failures/errors. It holds the real provider refresh, waits for logout's 204, releases the token response, and verifies the resolver gets 401, the session resolves absent, the original key contains only the sentinel, its TTL is positive and no greater than 30 days, no side tombstone key exists, and a later resolve is unauthorized.
- Focused Task 3 suite: `mvn -pl agent-server -Dtest='AuthSessionStoreTest,OAuth2ClientTest,CurrentUserArgumentResolverTest,AuthControllerTest' test` passed 71 tests, 0 failures/errors.
- Full server suite: `mvn -pl agent-server test` passed 231 tests, 0 failures/errors. `git diff --check` passed. Existing JVM/Netty/Mockito and unrelated Reactor worker warnings remain non-failing.

### Self-review and commit

- Both tombstone and update scripts touch only the original session key. There is no cross-process race window between checking logout state and writing refreshed JSON. `create()` still generates a fresh random token and writes the unchanged JSON session shape; it cannot reuse the logged-out app token.
- The sentinel contains no provider credentials. It is only treated as absent by session resolution, and expires on the same 30-day bound as a session. Owner-safe refresh-lock compare-delete, the 120-second lease, 75-second owner operation timeout, 105-second loser elapsed timeout, pre-refresh durable deletion, refresh-token rotation/nonrotation, and loser convergence remain unchanged.
- Mocked logout coverage now checks local invalidation precedes provider revoke; the real Redis race proves logout completes while refresh is still held and wins after refresh resumes. Provider revocation remains best effort when a session record is available; when pre-refresh invalidation already removed that record, the app token is still safely tombstoned and unusable.
- Commit: `fix: make logout win over in-flight OAuth2 refresh` (local only; no push).
- No additional concern found.
