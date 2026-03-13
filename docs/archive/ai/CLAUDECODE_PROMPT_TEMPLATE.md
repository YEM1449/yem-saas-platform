# ClaudeCode Prompt Template

Copy, fill placeholders, and keep sections explicit.

```text
You are a senior engineer working in this repository.

PRIMARY CONTEXT (read first)
- context/PROJECT_CONTEXT.md
- context/ARCHITECTURE.md
- context/DOMAIN_RULES.md
- context/SECURITY_BASELINE.md
- context/CONVENTIONS.md
- context/COMMANDS.md

PRODUCT / SPEC REFERENCES (if needed)
- docs/specs/CDC_Source.md
- docs/specs/Requirements_Index.md
- docs/specs/Backlog_Priorities.md
- docs/specs/Backlog_Status.md
- docs/specs/Gap_Analysis.md
- docs/specs/Implementation_Status.md

TASK
- Objective: [clear outcome]
- Scope in: [files/modules/areas]
- Scope out: [explicit exclusions]
- Constraints: [multi-tenancy, RBAC, migration policy, performance, compatibility]

ACCEPTANCE CRITERIA
1. [behavioral criterion]
2. [security/tenant criterion]
3. [test criterion]
4. [documentation criterion]

DELIVERABLES
- Implemented code changes
- Added/updated tests
- Updated docs/context for changed behavior only
- Short risk notes and follow-up items

EXECUTION RULES
- Use canonical commands from context/COMMANDS.md.
- Prefer minimal, focused diffs.
- Never edit applied Liquibase changesets; create new ones.
- Keep tenant checks and RBAC guarantees intact.
- If an assumption is needed, state it explicitly.

OUTPUT FORMAT
1. What changed
2. Why it changed
3. Files touched
4. Commands to verify
5. Remaining risks/open points
```
