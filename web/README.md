# agile-agent web

React 19 + TypeScript + Vite frontend for agile-agent. Login and chat talk to the server's OAuth2 and chat APIs.

## Local development

```bash
cd web
npm ci
npm run dev
```

The dev server listens on port 5173 and proxies `/api` to `VITE_API_PROXY_TARGET` (default `http://127.0.0.1:8088`). Leave `VITE_API_BASE_URL` empty so the browser calls same-origin `/api`. Copy `.env.example` to `.env` only when you need to override those values. Do not commit `.env`.

Sign-in is a full-page navigation to `/api/auth/oauth2/authorize`. After the identity provider returns, the server redirects to `OAUTH2_FRONTEND_REDIRECT_URI` (local default `http://localhost:5173/login/callback`) with `#token=...`. The app stores that token, removes the hash, and checks it with `GET /api/auth/me`.

`./start.sh` from the repository root starts this dev server when `web/node_modules` exists.

## Tests and production build

```bash
npm test
npm run build
```

`npm run build` typechecks and writes static files to `web/dist/`.

## Serving the static build

Production (https://ai.avacha.cloud) is nginx in front of the backend on the same origin. Build `web/dist` and point nginx at it. `/api` is proxied to the server; every other path falls back to `index.html` so `/login/callback` survives a refresh.

```nginx
server {
    listen 443 ssl;
    server_name ai.avacha.cloud;
    root /var/www/agile-agent;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8088;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        # Chat responses are SSE. Buffering holds the stream until the turn finishes.
        proxy_buffering off;
        proxy_read_timeout 3600s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Set `OAUTH2_FRONTEND_REDIRECT_URI=https://ai.avacha.cloud/login/callback` on the server, and register the backend callback (`/api/auth/oauth2/callback`) with the identity provider. `VITE_API_BASE_URL` stays empty for this same-origin layout.

For a local preview of the built files without nginx:

```bash
npm run build
npm run preview
```
