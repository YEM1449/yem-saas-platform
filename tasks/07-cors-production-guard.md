# Task 07 — CORS Production Safety Check

## Priority: 🟠 MEDIUM
## Risk: CORS misconfiguration in production allows cross-origin attacks
## Effort: 20 minutes

## Problem

`CORS_ALLOWED_ORIGINS` defaults to `http://localhost:4200,http://127.0.0.1:4200`. If deployed to production without changing this, the CRM is vulnerable to cross-origin request forgery.

## Files to Modify

### 1. `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/CorsConfig.java`

Add a startup validation. First, read the current file to understand its structure:
```bash
cat hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/CorsConfig.java
```

Add a `@PostConstruct` check or a configuration validator:

```java
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

@Value("${spring.profiles.active:default}")
private String activeProfile;

@PostConstruct
void validateCorsOrigins() {
    if (isProduction() && containsLocalhost()) {
        log.error("CRITICAL: CORS_ALLOWED_ORIGINS contains localhost in production profile! " +
                  "Set it to your exact frontend domain: CORS_ALLOWED_ORIGINS=https://app.yourdomain.com");
        throw new IllegalStateException(
            "CORS_ALLOWED_ORIGINS must not contain 'localhost' or '127.0.0.1' in production. " +
            "Current value includes development origins.");
    }
    if (containsLocalhost()) {
        log.warn("CORS_ALLOWED_ORIGINS contains localhost — acceptable for development only");
    }
}

private boolean isProduction() {
    return activeProfile != null && 
           (activeProfile.contains("production") || activeProfile.contains("prod"));
}

private boolean containsLocalhost() {
    // Check the actual allowed origins property
    // Implementation depends on how origins are stored in this class
    return false; // Replace with actual check
}
```

Adapt the implementation to match the actual field/property name used in `CorsConfig.java`.

## Alternative: Application-Production.yml

If a startup validation is too aggressive, add a warning-only check and document it in the production deployment guide.

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=CorsConfig*
```

## Acceptance Criteria

- [ ] Application refuses to start in `production` profile if CORS origins contain localhost
- [ ] Warning is logged in non-production profiles if localhost origins are detected
- [ ] All existing tests pass
