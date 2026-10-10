STATUS: complete

## Summary

- Added a loopback-only WebFlux mock OAuth2 provider with GET/POST authorization, form-only token and revocation endpoints, and Bearer userinfo claims.
- Authorization validates the exact configured redirect string on both requests, rechecks client/state/S256 PKCE/subject, and only redirects after issuing a one-use code. Token errors use safe OAuth error categories and do not include submitted credentials or grants.
- Added an automatically escaped Thymeleaf chooser, seeded with a platform admin and Tenant A/B administrators and users. Each tenant has root/team organization levels and distinct readable/manageable scope combinations.
- Default provider settings are client `agile-agent-local`, no client secret, port `8090`, and the same callback URI used by the agent server default (`http://localhost:8088/api/auth/oauth2/callback`). `MOCK_OAUTH2_CLIENT_ID`, `MOCK_OAUTH2_CLIENT_SECRET`, `MOCK_OAUTH2_REDIRECT_URIS`, and `MOCK_OAUTH2_PORT` can replace them.

## TDD and verification

- RED: `mvn -pl mock-oauth2 -Dtest=OAuth2ControllerTest test` failed with 10 tests and 10 failures; every endpoint request returned 404 because the HTTP endpoints did not exist yet.
- GREEN: `mvn -pl mock-oauth2 -Dtest=OAuth2ControllerTest test` — 10 tests, 0 failures, 0 errors.
- GREEN: `mvn -pl mock-oauth2 test` — 25 tests, 0 failures, 0 errors.
- `git diff --check` passed.

## Commit

`feat: expose the local mock OAuth2 provider`

## Self-check and concerns

- Tests cover exact redirect attack variants on GET and POST, POST parameter tampering, missing/wrong PKCE and state, one-use code exchange, client/grant error mapping, rotated refresh replay revoking its family, expired/revoked userinfo, unknown-token revoke, default claim shape, and token/code/secret non-disclosure in HTML/errors.
- The default client secret is intentionally blank for local development. Configure `MOCK_OAUTH2_CLIENT_SECRET` and the matching agent-server local-profile value when local tests need client authentication.
