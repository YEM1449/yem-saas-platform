# Module 08 — Error Handling

## Learning Objectives

- Describe the `ErrorResponse` envelope format
- Add a new domain exception with the correct `ErrorCode`
- Understand how `GlobalExceptionHandler` maps exceptions to HTTP responses

---

## Error Envelope

All API error responses use a consistent JSON envelope:

```json
{
  "timestamp": "2026-03-17T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "CONTACT_NOT_FOUND",
  "message": "Contact not found",
  "path": "/api/contacts/550e8400-..."
}
```

For validation errors (400), the response includes a `fieldErrors` array:

```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "fieldErrors": [
    {"field": "email", "message": "must be a valid email"},
    {"field": "fullName", "message": "must not be blank"}
  ]
}
```

---

## ErrorCode Enum

`common/error/ErrorCode.java` defines every machine-readable error code. Clients use the `code` field to distinguish error types programmatically (not the HTTP status, which can be the same for different errors).

Partial listing:

| Code | HTTP | Meaning |
|------|------|---------|
| `VALIDATION_ERROR` | 400 | Bean validation failure |
| `UNAUTHORIZED` | 401 | Missing or invalid JWT |
| `ACCOUNT_LOCKED` | 401 | Too many failed logins |
| `FORBIDDEN` | 403 | Insufficient role |
| `NOT_FOUND` | 404 | Generic not found |
| `CONTACT_NOT_FOUND` | 404 | Contact not found |
| `PROPERTY_ALREADY_SOLD` | 409 | Contract on already-sold property |
| `GDPR_ERASURE_BLOCKED` | 409 | Signed contract prevents erasure |
| `RATE_LIMIT_EXCEEDED` | 429 | Rate limit exceeded |
| `INTERNAL_ERROR` | 500 | Unhandled exception |

---

## Adding a New Domain Exception

**Step 1:** Create the exception class:

```java
// example/service/ExampleNotFoundException.java
public class ExampleNotFoundException extends RuntimeException {
    public ExampleNotFoundException() {
        super("Example not found");
    }
}
```

**Step 2:** Add an `ErrorCode` value:

```java
// common/error/ErrorCode.java
public enum ErrorCode {
    // ... existing values ...
    EXAMPLE_NOT_FOUND
}
```

**Step 3:** Register a handler in `GlobalExceptionHandler`:

```java
@ExceptionHandler(ExampleNotFoundException.class)
@ResponseStatus(HttpStatus.NOT_FOUND)
public ErrorResponse handleExampleNotFound(ExampleNotFoundException ex, HttpServletRequest req) {
    return ErrorResponse.of(HttpStatus.NOT_FOUND, ErrorCode.EXAMPLE_NOT_FOUND, req);
}
```

**Step 4:** Throw it from the service:

```java
return repo.findByTenantIdAndId(tenantId, id)
    .orElseThrow(ExampleNotFoundException::new);
```

---

## GlobalExceptionHandler

`common/error/GlobalExceptionHandler.java` is a `@RestControllerAdvice` — it intercepts exceptions from all controllers.

Order of handler selection: Spring uses the most specific matching exception type. If `ContactNotFoundException extends EntityNotFoundException extends RuntimeException`, Spring selects `ContactNotFoundException` handler over the generic `RuntimeException` handler.

---

## Source Files

| File | Purpose |
|------|---------|
| `common/error/ErrorResponse.java` | The error envelope record |
| `common/error/ErrorCode.java` | All error codes enum |
| `common/error/GlobalExceptionHandler.java` | Exception to HTTP response mapping |
| `contact/service/ContactNotFoundException.java` | Example domain exception |

---

## Exercise

1. Open `GlobalExceptionHandler.java` and count the exception handler methods.
2. Find the handler for `MethodArgumentNotValidException` (validation errors).
3. Verify it builds a `fieldErrors` list from `ex.getBindingResult().getFieldErrors()`.
4. Add a new `ExampleAlreadyExistsException` with error code `EXAMPLE_ALREADY_EXISTS` that maps to HTTP 409. Write a unit test for the handler.
