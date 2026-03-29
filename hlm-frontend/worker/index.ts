/**
 * Cloudflare Worker — reverse proxy for the HLM SaaS backend
 *
 * Deployment target: Cloudflare Workers + Assets (wrangler deploy)
 * NOT Cloudflare Pages — do not use wrangler pages deploy.
 *
 * Request routing:
 *   /api/*       → https://yem-hlm-backend.onrender.com
 *   /auth/*      → https://yem-hlm-backend.onrender.com
 *   /actuator/*  → https://yem-hlm-backend.onrender.com
 *   OPTIONS      → CORS preflight response (no upstream call)
 *   /*           → static assets served by env.ASSETS (Angular SPA)
 *
 * The ASSETS binding is declared in wrangler.toml:
 *   [assets]
 *   directory = "./dist/frontend/browser"
 *   binding   = "ASSETS"
 */

export interface Env {
  ASSETS: Fetcher;
}

const BACKEND = "https://yem-hlm-backend.onrender.com";

const PROXY_PREFIXES = ["/api/", "/auth/", "/actuator/"];

/**
 * Headers that must NOT be forwarded to the backend when proxying.
 *
 * Origin and Referer are browser-to-Worker headers that reflect the
 * Cloudflare domain (e.g. https://yem-hlm.workers.dev). Forwarding them
 * causes Spring Security's CorsFilter to reject the request with 403
 * "Invalid CORS request" because that domain is not in the backend's
 * CORS_ALLOWED_ORIGINS list.
 *
 * The Worker→backend call is a server-to-server hop; CORS does not apply
 * to it. Stripping these headers makes the backend treat the request as
 * a same-origin server call and skips the CORS rejection entirely.
 */
const STRIP_HEADERS = new Set([
  "origin",
  "referer",
]);

function buildProxyHeaders(incoming: Headers): Headers {
  const headers = new Headers();
  for (const [key, value] of incoming.entries()) {
    if (!STRIP_HEADERS.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  }
  return headers;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // ── CORS preflight ────────────────────────────────────────────────────────
    // The browser sends OPTIONS to the Worker (same-origin from the browser's
    // perspective). Reply here so the backend never sees preflight traffic.
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

      return fetch(
        new Request(backendUrl, {
          method: request.method,
          headers: buildProxyHeaders(request.headers), // Origin/Referer stripped
          body: ["GET", "HEAD"].includes(request.method) ? null : request.body,
          redirect: "follow",
        })
      );
    }

    // ── Static assets (SPA) ───────────────────────────────────────────────────
    // Workers Assets serves index.html for unmatched routes automatically.
    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;
