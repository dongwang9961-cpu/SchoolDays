# SchoolDays Frontend

Vite frontend for the SchoolDays web application, built with vanilla JavaScript.

## Development

Install dependencies:

```bash
npm install
```

Start the Vite dev server:

```bash
npm run dev
```

By default, Vite runs on `http://localhost:5173`.

The initial screen is a vanilla JavaScript login page wired to `POST /api/auth/login`. Successful login stores the access token in `localStorage` under `schooldays.accessToken`.

## Backend Proxy

The dev server proxies these paths to the Spring Boot backend at `http://localhost:8080`:

- `/api`
- `/actuator`

That means frontend code can call backend APIs with relative URLs:

```ts
await fetch("/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, password }),
});
```

Start the backend from `../backend` before using proxied API calls.

## Production API

For local development, leave `VITE_API_BASE_URL` unset so Vite can proxy relative `/api` calls to `http://localhost:8080`.

For a production frontend build that talks to the production API server, set:

```bash
VITE_API_BASE_URL=https://api.schooldays.cc
```

## Google Places Autocomplete

The school admin site form can use Google Places Autocomplete for address entry. Create a frontend environment file with a Google Maps JavaScript API key that has the Places library enabled:

```bash
VITE_GOOGLE_MAPS_API_KEY=your-google-maps-api-key
```

If the key is not configured, the form falls back to browser-native suggestions and manual city/state/ZIP entry.
