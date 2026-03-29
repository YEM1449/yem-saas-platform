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

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
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

      return fetch(
        new Request(backendUrl, {
          method: request.method,
          headers: request.headers,
          body: ["GET", "HEAD"].includes(request.method) ? null : request.body,
          redirect: "follow",
        })
      );
    }

    // ── Static assets (SPA) ───────────────────────────────────────────────────
    // Workers Assets serves index.html for unmatched routes automatically
    // when not_found_handling = "single-page-application" is set, or falls
    // back to the _redirects file in the assets directory.
    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;
