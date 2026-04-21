# HTTPS And TLS Guide

This guide explains how transport security is expected to work in deployment.

## 1. Recommended Production Topology

```text
Internet
  -> Nginx or cloud reverse proxy (TLS termination)
  -> Angular static assets
  -> Spring Boot on internal/plain HTTP
```

Reference config: [../../../nginx/nginx.conf](../../../nginx/nginx.conf)

## 2. Why Reverse Proxy TLS Is The Default

- simpler certificate management
- clearer separation between transport and application concerns
- easier static asset delivery and header management

## 3. Critical Application Settings

- `FORWARD_HEADERS_STRATEGY=FRAMEWORK`
- secure cookies enabled in production
- exact `CORS_ALLOWED_ORIGINS`
- correct `FRONTEND_BASE_URL` and `PORTAL_BASE_URL`

## 4. Cookie Implications

Browser session cookies rely on correct TLS behavior.

Important consequences:

- `Secure=true` requires HTTPS
- wrong proxy headers can break redirect logic or session assumptions
- portal and CRM cookies have different paths and must both be validated after auth changes

## 5. HTTP Headers And Browser Protections

The platform sets:

- CSP
- frame denial
- content-type nosniff
- permissions policy
- optional HSTS

The proxy and application should not contradict each other on these controls.

## 6. CORS And SameSite

- keep allowed origins explicit
- `SameSite=Lax` is the default operational compromise
- same-domain deployments are the easiest way to avoid cross-origin cookie pain

## 7. Embedded TLS Option

Spring Boot can also run with embedded TLS for selected environments, but this is not the primary production model.

Use it when:

- there is no external reverse proxy
- you are testing TLS locally or in a controlled staging setup

## 8. Production Checklist

- valid certificate
- correct domain names in URLs
- `Secure` cookies enabled
- trusted forward headers configured
- exact CORS origins configured
- health endpoints reachable as intended
