# ClaudeCode Prompt Template

Copy/paste and fill the brackets.

```text
You are Claude Code acting as a senior engineer on this repo.

SOURCES OF TRUTH
- docs/spec/CDC_Source.md
- docs/spec/Requirements_Index.md
- docs/spec/Backlog_Priorities.md
- docs/spec/Backlog_Status.md
- docs/spec/Gap_Analysis.md
- docs/spec/Implementation_Status.md
- docs/ai/PROJECT_CONTEXT.md

TASK
- Implement: [FEATURE / REQUIREMENT IDs]
- Constraints: [security, performance, multi-tenant, migration rules, etc.]

ACCEPTANCE CRITERIA
- [bullet list]

DELIVERABLES
- Code changes + migrations
- Tests updated/added
- Docs updated (context/spec) with only changed facts

EXECUTION
- Do not run tests in your environment. I will run them locally.
- Provide exact commands for me to run.
```
