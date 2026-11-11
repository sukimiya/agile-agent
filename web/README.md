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

## Low energy palette

Surfaces stay black, white, and gray. Named accents live as Tailwind theme tokens in `src/index.css`:

| Token | Name | Hex |
| --- | --- | --- |
| `medium-violet-red` | Medium Violet Red | `#D21697` |
| `fresh-sky` | Fresh Sky | `#54B0DE` |
| `blush-pop` | Blush Pop | `#EBABD7` |
| `frozen-water` | Frozen Water | `#D1F2E3` |
| `frozen-water-mountain-lake` | Frozen Water, Mountain Lake | `#DBF1E7` |
| `slime-lime` | Slime Lime | `#AEF016` |

Medium Violet Red is the logo and non-button highlights. Fresh Sky is input borders, input shadows, and button fill, border, and label chrome. Solid Fresh Sky buttons use dark `#171717` text because white on `#54B0DE` is about 2.5:1. Hover uses a darker Fresh Sky (`#3A96C4`) so that dark label stays above 4.5:1. Slime Lime is only a warning chip background, also with dark text. `#D1F2E3` and `#DBF1E7` are both Frozen Water; `#DBF1E7` leans Mountain Lake.

## Day and night

The theme control is 白天 (light), 夜晚 (dark), or 自动 (auto). Auto is the default when nothing is saved in `localStorage` (`theme-mode`). Auto follows `prefers-color-scheme` and updates if the OS setting changes. A manual choice stays until it is changed again. A short script in `index.html` applies the saved or system theme before the app paints.

Light mode is the palette above. Dark mode keeps those accent hexes and shifts the gray surfaces: near-black canvas and cards, light-gray text, and darkened Frozen Water washes so selected conversations and hint chips stay readable. History titles stay a neutral gray (`#57534e` in light, `#C8C8C8` in dark).

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
