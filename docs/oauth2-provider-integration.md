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
| `OAUTH2_DEFAULT_ACCESS_TOKEN_TTL` | Access-token lifetime assumed when the token response has no `expires_in`; default `1h`. |
| `OAUTH2_USER_AGENT` | `User-Agent` sent on token, user-info, and revocation requests; default `agile-agent`. |

Production endpoint URLs, the callback URI, frontend redirect URI, and public application URL must use HTTPS. This is a production deployment requirement; the application currently does not enforce URL schemes at startup or runtime. Production operators must ensure HTTPS is used. Register the exact callback URI with the provider. HTTP URLs are suitable only for loopback development. If TLS terminates at a reverse proxy, configure the public HTTPS callback and frontend URLs explicitly rather than registering an internal container address.

Do not place client credentials, signing keys, or `LLM_CONFIG_SECRET` in the Docker image, Compose defaults, source control, or build arguments. The deployment environment must inject all secrets. The same `LLM_CONFIG_SECRET` must be used by all server instances that share encrypted tenant LLM configuration.

## Map user-info claims

The user-info response is read with JSON Pointers. Defaults below match the local mock provider; change the pointer when the real provider uses a different JSON shape. Each pointer can address a nested value, for example `/organization/id`.

| Variable | Default JSON Pointer | Expected value |
| --- | --- | --- |
| `OAUTH2_CLAIM_SUBJECT` | `/sub` | Non-empty string or integer; stable user identifier. |
| `OAUTH2_CLAIM_SUBJECT_PREFIX` | empty | Text prepended to the subject, for example `github:`, so ids from different providers cannot collide. |
| `OAUTH2_CLAIM_DISPLAY_NAME` | `/name` | String shown as the user's name. |
| `OAUTH2_CLAIM_DISPLAY_NAME_FALLBACK` | empty | Pointer read when the display name is missing, null, or blank. |
| `OAUTH2_CLAIM_TENANT_ID` | `/tenant_id` | Optional tenant identifier string. |
| `OAUTH2_CLAIM_TENANT_NAME` | `/tenant_name` | Optional tenant display name string. |
| `OAUTH2_CLAIM_PRIMARY_ORG_ID` | `/primary_org/id` | Optional primary organization identifier string. |
| `OAUTH2_CLAIM_PRIMARY_ORG_NAME` | `/primary_org/name` | Optional primary organization display name string. |
| `OAUTH2_CLAIM_ROLES` | `/roles` | Array of strings; use an empty array if the user has no roles. |
| `OAUTH2_CLAIM_MANAGEABLE_ORG_IDS` | `/manageable_org_ids` | Array of organization IDs the user may manage; use an empty array when none. |
| `OAUTH2_CLAIM_READABLE_ORG_IDS` | `/readable_org_ids` | Array of organization IDs the user may read; use an empty array when none. |

The subject, display name, roles, manageable-organization IDs, and readable-organization IDs must be present with the expected types. Tenant and primary-organization fields may be omitted when the identity has no such scope. The mapped roles are compared with `OAUTH2_PLATFORM_ADMIN_ROLE` and `OAUTH2_TENANT_ADMIN_ROLE`; use the identity provider's exact role values. These values determine authorization scope, so verify the claim shape and role mapping with representative identities before production rollout.

The token endpoint is called with `Accept: application/json`. A token response with an `error` field or without `access_token` fails login even when its HTTP status is 200. When the provider returns no refresh token, the session ends once the access token reaches its lifetime and the user signs in again.

### Claim defaults

Some providers return no tenant, role, or organization data. Set `OAUTH2_CLAIM_DEFAULTS_ENABLED=true` to fill a claim that is missing or null with a configured value. A claim that is present always wins and is still type-checked. While defaults are disabled (the default), a missing required claim fails login as described above.

| Variable | Used when the user-info response lacks |
| --- | --- |
| `OAUTH2_CLAIM_DEFAULTS_ENABLED` | Turns claim defaults on; default `false`. |
| `OAUTH2_DEFAULT_TENANT_ID` / `OAUTH2_DEFAULT_TENANT_NAME` | Tenant id and name. |
| `OAUTH2_DEFAULT_PRIMARY_ORG_ID` / `OAUTH2_DEFAULT_PRIMARY_ORG_NAME` | Primary organization id and name. |
| `OAUTH2_DEFAULT_ROLES` | Roles, comma-separated. |
| `OAUTH2_DEFAULT_MANAGEABLE_ORG_IDS` | Manageable organization ids, comma-separated; empty means none. |
| `OAUTH2_DEFAULT_READABLE_ORG_IDS` | Readable organization ids, comma-separated. |

Tenant-scoped features, including chat and tenant LLM configuration, need a tenant, so set a default tenant when the provider has none.

### Admin grant

An admin grant adds roles and organization access to selected identities. The server reads the user-info value at `OAUTH2_ADMIN_MATCH_CLAIM` (a JSON Pointer; a string or integer) and compares it, without the subject prefix, with `OAUTH2_ADMIN_MATCH_VALUES`. The grant is off unless both are set. Match an immutable id such as GitHub `/id` rather than a login that can be renamed and later reused by someone else.

| Variable | Purpose |
| --- | --- |
| `OAUTH2_ADMIN_MATCH_CLAIM` | Pointer to the matched user-info value, for example `/id`. |
| `OAUTH2_ADMIN_MATCH_VALUES` | Comma-separated values that receive the grant. |
| `OAUTH2_ADMIN_ROLES` | Roles added, for example `platform_admin,tenant_admin`. |
| `OAUTH2_ADMIN_MANAGEABLE_ORG_IDS` | Manageable organization ids added. |
| `OAUTH2_ADMIN_READABLE_ORG_IDS` | Readable organization ids added. |

Platform administration (provider presets, memory administration) needs the platform admin role. Writing a tenant's LLM configuration needs the tenant admin role and a tenant, so an operator who configures model keys needs both roles.

### Example: GitHub OAuth App

GitHub OAuth Apps provide sign-in without an identity-provider subscription. Create a classic OAuth App (not a GitHub App) under GitHub Settings > Developer settings > OAuth Apps, set the homepage URL, and register the exact callback URL. GitHub's `/user` response has a numeric `id`, a `login`, a nullable `name`, and no tenant, role, or organization claims; GitHub issues no refresh token and its token response has no `expires_in`. This example gives every user the role `user` with read-only access to one organization, and gives the listed GitHub account ids administrator roles:

```bash
OAUTH2_AUTHORIZATION_URI=https://github.com/login/oauth/authorize
OAUTH2_TOKEN_URI=https://github.com/login/oauth/access_token
OAUTH2_USERINFO_URI=https://api.github.com/user
OAUTH2_REVOCATION_URI=
OAUTH2_CLIENT_ID=<oauth-app-client-id>
OAUTH2_CLIENT_SECRET=<from your secret manager>
OAUTH2_REDIRECT_URI=https://agent.example.com/api/auth/oauth2/callback
OAUTH2_FRONTEND_REDIRECT_URI=https://agent.example.com/login/callback
OAUTH2_SCOPE=read:user user:email
OAUTH2_DEFAULT_ACCESS_TOKEN_TTL=8h

OAUTH2_CLAIM_SUBJECT=/id
OAUTH2_CLAIM_SUBJECT_PREFIX=github:
OAUTH2_CLAIM_DISPLAY_NAME=/name
OAUTH2_CLAIM_DISPLAY_NAME_FALLBACK=/login

OAUTH2_CLAIM_DEFAULTS_ENABLED=true
OAUTH2_DEFAULT_TENANT_ID=users.example.com
OAUTH2_DEFAULT_TENANT_NAME=users.example.com
OAUTH2_DEFAULT_PRIMARY_ORG_ID=users.example.com
OAUTH2_DEFAULT_PRIMARY_ORG_NAME=users.example.com
OAUTH2_DEFAULT_ROLES=user
OAUTH2_DEFAULT_MANAGEABLE_ORG_IDS=
OAUTH2_DEFAULT_READABLE_ORG_IDS=users.example.com

OAUTH2_ADMIN_MATCH_CLAIM=/id
OAUTH2_ADMIN_MATCH_VALUES=<numeric GitHub account id>
OAUTH2_ADMIN_ROLES=platform_admin,tenant_admin
OAUTH2_ADMIN_MANAGEABLE_ORG_IDS=users.example.com
OAUTH2_ADMIN_READABLE_ORG_IDS=users.example.com
```

A GitHub account's numeric id is the `id` field at `https://api.github.com/users/<login>`. GitHub has no token revocation endpoint for this flow, so leave `OAUTH2_REVOCATION_URI` empty; logout ends the application session only. Users can revoke the app's access under their GitHub settings. Every user sees `OAUTH2_DEFAULT_TENANT_ID` as their tenant, so all GitHub users share that tenant's LLM configuration. `OAUTH2_DEFAULT_ACCESS_TOKEN_TTL` controls how long a session lasts before the user must sign in again; user info is still re-read on the normal identity freshness schedule, so a revoked GitHub token stops working then.

The server exchanges the authorization code and fetches user info from the configured endpoints. Keep the provider's TLS certificate validation enabled and expose only the public HTTPS callback through the edge proxy. The provider must allow the configured callback and frontend redirect destinations.

## Local mock provider

For isolated development, use the Compose `local-mock` profile described in the [README Quick start](../README.md#quick-start) and [local development notes](local-development.md). It seeds these identities:

| Subject | Role | Tenant | Primary organization |
| --- | --- | --- | --- |
| `platform-admin` | `platform_admin` | Platform Tenant (`tenant-platform`) | Platform Root (`platform-root`) |
| `tenant-a-admin` | `tenant_admin` | Tenant A (`tenant-a`) | Tenant A Team 1 (`tenant-a-team-1`) |
| `tenant-a-user` | `user` | Tenant A (`tenant-a`) | Tenant A Team 1 (`tenant-a-team-1`) |
| `tenant-b-admin` | `tenant_admin` | Tenant B (`tenant-b`) | Tenant B Team 1 (`tenant-b-team-1`) |
| `tenant-b-user` | `user` | Tenant B (`tenant-b`) | Tenant B Team 1 (`tenant-b-team-1`) |

These are local fixtures. The mock provider is for development and isolated tests only; use a real provider for any shared or production deployment.

The Compose `local-mock` profile also has disposable MySQL development credential defaults (`local-mock-password` and `local-mock-root-password`) and an empty mock OAuth2 client secret. These fixtures exist only to make an isolated local profile convenient; do not reuse them for shared environments or production. Production database, OAuth2, and LLM encryption credentials must come from external configuration or a secret manager.
