# Sales Test Plan (MVP)
Date: 2026-02-26

## Integration Tests (recommended)
1) Contract creation
- create draft contract with ACTIVE project -> 201
- create contract with ARCHIVED project -> 400
- create contract with cross-tenant property/project -> 404

2) Signing contract
- sign draft contract -> 200
- property status becomes SOLD
- cannot sign second contract for same property -> 409/400 (depending on convention)

3) Cancel contract
- cancel signed contract -> 200
- property reverts to AVAILABLE (or RESERVED if active deposit exists) [OPEN POINT]

4) KPI aggregation tests
- salesCount/salesTotalAmount correct for seeded contracts
- salesByProject and salesByAgent correct
- agent RBAC: agent sees only own contracts
- tenant isolation: cannot view cross-tenant

## Unit Tests
- validation helpers for ACTIVE project check
- discount calculation (if listPrice present)
