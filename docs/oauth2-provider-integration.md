# Integrate an OAuth2 identity provider

Agile Agent accepts OAuth2 authorization-code login through a provider's authorization, token, user-info, and optional revocation endpoints. The endpoints are deployment configuration; `agent-server` does not depend on a particular provider implementation. A future provider can replace the configured endpoints or be packaged as a separate Maven module without coupling the server to that provider. The application does not require the development mock provider. Production must point these values at the organization's real identity provider and must not activate the Compose `local-mock` profile.

## Configure the provider

Set the required production values through the deployment environment or an external configuration/secret manager. Production must supply its endpoint URLs and client credentials; the mock endpoint defaults and local fixtures are not production settings. The server reads the following environment variables:

| Variable | Purpose |
| --- | --- |
| `OAUTH2_AUTHORIZATION_URI` | Provider authorization endpoint. |
| `OAUTH2_TOKEN_URI` | Provider token endpoint. |
| `OAUTH2_USERINFO_URI` | Endpoint that returns the authenticated user's JSON claims. |
| `OAUTH2_REVOCATION_URI` | Optional token revocation endpoint. |
| `OAUTH2_CLIENT_ID` | Client registered with the provider. |
| `OAUTH2_CLIENT_SECRET` | Client credential; supply it through a secret manager when required by the provider. |
| `OAUTH2_REDIRECT_URI` | Public callback URL registered with the provider; the default path is `/api/auth/oauth2/callback`. |
| `OAUTH2_FRONTEND_REDIRECT_URI` | Browser destination after login completes. |
| `OAUTH2_SCOPE` | Space-separated OAuth2 scopes; the default is `openid profile offline_access`. |
| `OAUTH2_PLATFORM_ADMIN_ROLE` | Role value that maps to the platform administrator flag. |
| `OAUTH2_TENANT_ADMIN_ROLE` | Role value that maps to the tenant administrator flag. |

Production endpoint URLs, the callback URI, frontend redirect URI, and public application URL must use HTTPS. This is a production deployment requirement; the application currently does not enforce URL schemes at startup or runtime. Production operators must ensure HTTPS is used. Register the exact callback URI with the provider. HTTP URLs are suitable only for loopback development. If TLS terminates at a reverse proxy, configure the public HTTPS callback and frontend URLs explicitly rather than registering an internal container address.

Do not place client credentials, signing keys, or `LLM_CONFIG_SECRET` in the Docker image, Compose defaults, source control, or build arguments. The deployment environment must inject all secrets. The same `LLM_CONFIG_SECRET` must be used by all server instances that share encrypted tenant LLM configuration.

## Map user-info claims

The user-info response is read with JSON Pointers. Defaults below match the local mock provider; change the pointer when the real provider uses a different JSON shape. Each pointer can address a nested value, for example `/organization/id`.

| Variable | Default JSON Pointer | Expected value |
| --- | --- | --- |
| `OAUTH2_CLAIM_SUBJECT` | `/sub` | Non-empty string; stable user identifier. |
| `OAUTH2_CLAIM_DISPLAY_NAME` | `/name` | String shown as the user's name. |
| `OAUTH2_CLAIM_TENANT_ID` | `/tenant_id` | Optional tenant identifier string. |
| `OAUTH2_CLAIM_TENANT_NAME` | `/tenant_name` | Optional tenant display name string. |
| `OAUTH2_CLAIM_PRIMARY_ORG_ID` | `/primary_org/id` | Optional primary organization identifier string. |
| `OAUTH2_CLAIM_PRIMARY_ORG_NAME` | `/primary_org/name` | Optional primary organization display name string. |
| `OAUTH2_CLAIM_ROLES` | `/roles` | Array of strings; use an empty array if the user has no roles. |
| `OAUTH2_CLAIM_MANAGEABLE_ORG_IDS` | `/manageable_org_ids` | Array of organization IDs the user may manage; use an empty array when none. |
| `OAUTH2_CLAIM_READABLE_ORG_IDS` | `/readable_org_ids` | Array of organization IDs the user may read; use an empty array when none. |

The subject, display name, roles, manageable-organization IDs, and readable-organization IDs must be present with the expected types. Tenant and primary-organization fields may be omitted when the identity has no such scope. The mapped roles are compared with `OAUTH2_PLATFORM_ADMIN_ROLE` and `OAUTH2_TENANT_ADMIN_ROLE`; use the identity provider's exact role values. These values determine authorization scope, so verify the claim shape and role mapping with representative identities before production rollout.

The server exchanges the authorization code and fetches user info from the configured endpoints. Keep the provider's TLS certificate validation enabled and expose only the public HTTPS callback through the edge proxy. The provider must allow the configured callback and frontend redirect destinations.

## Local mock provider

For isolated development, use the Compose `local-mock` profile described in the [README](../README.md). It seeds these identities:

| Subject | Role | Tenant | Primary organization |
| --- | --- | --- | --- |
| `platform-admin` | `platform_admin` | Platform Tenant (`tenant-platform`) | Platform Root (`platform-root`) |
| `tenant-a-admin` | `tenant_admin` | Tenant A (`tenant-a`) | Tenant A Team 1 (`tenant-a-team-1`) |
| `tenant-a-user` | `user` | Tenant A (`tenant-a`) | Tenant A Team 1 (`tenant-a-team-1`) |
| `tenant-b-admin` | `tenant_admin` | Tenant B (`tenant-b`) | Tenant B Team 1 (`tenant-b-team-1`) |
| `tenant-b-user` | `user` | Tenant B (`tenant-b`) | Tenant B Team 1 (`tenant-b-team-1`) |

These are local fixtures. The mock provider is for development and isolated tests only; use a real provider for any shared or production deployment.

The Compose `local-mock` profile also has disposable MySQL development credential defaults (`local-mock-password` and `local-mock-root-password`) and an empty mock OAuth2 client secret. These fixtures exist only to make an isolated local profile convenient; do not reuse them for shared environments or production. Production database, OAuth2, and LLM encryption credentials must come from external configuration or a secret manager.
