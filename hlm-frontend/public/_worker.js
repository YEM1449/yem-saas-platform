/**
 * Cloudflare Pages Advanced Mode Worker
 *
 * Routes /api/*, /auth/*, /actuator/* to the Render backend.
 * Everything else is served from Pages static assets.
 *
 * Why this file exists:
 *   Cloudflare Pages _redirects does NOT support proxying (status 200 rewrites)
 *   to external absolute URLs — error 10021. The correct approach is a Worker
 *   that forwards matching requests to the backend and delegates the rest to
 *   the Pages asset-serving runtime via env.ASSETS.fetch().
 *
 * Deployment:
 *   This file lives in public/ and Angular copies it verbatim to
 *   dist/frontend/browser/_worker.js on every build. Cloudflare Pages
 *   detects _worker.js at the deploy root and activates Advanced Mode
 *   automatically — no dashboard toggle required.
 */

const BACKEND = "https://yem-hlm-backend.onrender.com";

const PROXY_PREFIXES = ["/api/", "/auth/", "/actuator/"];

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // ── CORS preflight ────────────────────────────────────────────────────────
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
          "Access-Control-Max-Age": "86400",
        },
      });
    }

    // ── Backend proxy ─────────────────────────────────────────────────────────
    const shouldProxy = PROXY_PREFIXES.some((p) => url.pathname.startsWith(p));

    if (shouldProxy) {
      const backendUrl = BACKEND + url.pathname + url.search;

      const proxyRequest = new Request(backendUrl, {
        method: request.method,
        headers: request.headers,
        body: ["GET", "HEAD"].includes(request.method) ? null : request.body,
        redirect: "follow",
      });

      return fetch(proxyRequest);
    }

    // ── Static assets (SPA) ───────────────────────────────────────────────────
    return env.ASSETS.fetch(request);
  },
};
