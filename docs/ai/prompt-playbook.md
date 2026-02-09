# Prompt Playbook (Low-Token)

Use these templates to keep responses short, grounded, and citation-ready.

## Required output format (agents)
- **Files inspected**
- **Plan**
- **Changed files**
- **Minimal diff summary**
- **Tests to run**
- **Open Questions**

## Template: Bug Fix
- Goal: Fix <issue>.
- Constraints: No secrets. No behavior changes outside scope.
- Output: Use required format; include file-path citations.

## Template: Feature
- Goal: Add <feature>.
- Constraints: Tenant isolation, RBAC, error contract.
- Output: Use required format; include file-path citations.

## Template: Test
- Goal: Add/adjust tests for <component>.
- Constraints: Use existing IntegrationTestBase/Testcontainers.
- Output: Use required format; include file-path citations.

## Template: Refactor
- Goal: Refactor <area> without behavior changes.
- Constraints: Keep API + DB stable.
- Output: Use required format; include file-path citations.
