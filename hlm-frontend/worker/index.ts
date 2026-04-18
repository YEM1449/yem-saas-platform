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

/**
 * Security headers added to every response served by this Worker.
 * These cannot be set as <meta http-equiv> tags — browsers ignore them
 * for X-Frame-Options and X-Content-Type-Options.
 */
const SECURITY_HEADERS: Record<string, string> = {
  "X-Frame-Options": "DENY",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "strict-origin-when-cross-origin",
};

function applySecurityHeaders(response: Response): Response {
  const patched = new Response(response.body, response);
  for (const [key, value] of Object.entries(SECURITY_HEADERS)) {
    patched.headers.set(key, value);
  }
  return patched;
}

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
  /**
   * Cron handler — keeps the Render backend warm.
   *
   * Triggered every 14 min by [triggers] crons = ["*\/14 * * * *"] in wrangler.toml.
   * Render free/starter idles after 15 min; Spring Boot cold-start takes 30-90 s
   * which exceeds Cloudflare Workers' 30 s wall-clock cap → HTTP 524 on login.
   * A lightweight GET /actuator/health ping is enough to reset the idle timer.
   */
  async scheduled(_event: ScheduledEvent, _env: Env): Promise<void> {
    try {
      await fetch(BACKEND + "/actuator/health", { method: "GET" });
    } catch {
      console.error("[warmup] health ping failed");
    }
  },

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

      const backendResponse = await fetch(
        new Request(backendUrl, {
          method: request.method,
          headers: buildProxyHeaders(request.headers), // Origin/Referer stripped
          body: ["GET", "HEAD"].includes(request.method) ? null : request.body,
          redirect: "follow",
        })
      );
      return applySecurityHeaders(backendResponse);
    }

    // ── Static assets (SPA) ───────────────────────────────────────────────────
    // Workers Assets serves index.html for unmatched routes automatically.
    const assetResponse = await env.ASSETS.fetch(request);
    return applySecurityHeaders(assetResponse);
  },
} satisfies ExportedHandler<Env>;
