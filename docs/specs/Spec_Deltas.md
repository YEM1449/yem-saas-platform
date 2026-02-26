# Spec Deltas (CDC vs Implementation)

Record intentional deviations here.

## Delta Template
- **Date:** YYYY-MM-DD
- **Area:** (module)
- **CDC says:** …
- **Implementation:** …
- **Reason:** …
- **Impact:** (DB/API/UI/migration/tests)

- **Date:** 2026-02-26
- **Area:** Security / Auth (JWT)
- **CDC says:** Gestion fine des permissions par société, projet et module.
- **Implementation:** Add immediate session invalidation when role changes or a user is disabled (revoke existing JWT sessions).
- **Reason:** Prevent privilege persistence until token expiry; make admin actions effective immediately.
- **Impact:** Auth filter/validator + user service; tests for role demotion/disable; documentation update.
