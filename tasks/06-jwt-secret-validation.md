# Task 06 — JWT Secret Minimum Length Validation at Startup

## Priority: 🟢 LOW
## Risk: Weak JWT secrets could compromise all tokens
## Effort: 20 minutes

## Problem

`JWT_SECRET` is required but has no minimum length enforcement. A weak secret (e.g., "secret") makes JWT forgery trivial.

## Files to Modify

### 1. `hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/JwtProperties.java`

Add a `@PostConstruct` validation (or use `@Validated` with a custom validator):

```java
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Inside the class:

private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);
private static final int MIN_SECRET_LENGTH = 32; // 256 bits minimum

@PostConstruct
void validateSecret() {
    if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
        throw new IllegalStateException(
            String.format("JWT_SECRET must be at least %d characters (256 bits). Current length: %d. " +
                "Generate one with: openssl rand -base64 48",
                MIN_SECRET_LENGTH,
                secret == null ? 0 : secret.length()));
    }
    log.info("JWT secret validated: {} characters", secret.length());
}
```

First, check the current structure of `JwtProperties.java` to see how the secret is loaded:
```bash
cat hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/JwtProperties.java
```

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=JwtBeansConfigTest
```

## Acceptance Criteria

- [ ] Application fails to start if `JWT_SECRET` is shorter than 32 characters
- [ ] Error message includes the generate command
- [ ] Existing tests that set a valid secret still pass
