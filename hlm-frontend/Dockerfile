# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /app

# Cache npm deps separately from source
COPY package.json package-lock.json ./
RUN npm ci --prefer-offline --no-audit

COPY . .
RUN npm run build -- --configuration production

# ── Stage 2: Runtime (nginx) ─────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# curl for a reliable health probe (wget in BusyBox exits 1 on connection-refused
# but also fails silently on some Alpine builds; curl -f is unambiguous)
RUN apk add --no-cache curl

# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*

# Copy built Angular app (Angular 19 output goes to dist/frontend/browser)
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# Copy custom nginx config
COPY nginx.frontend.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost/ || exit 1
