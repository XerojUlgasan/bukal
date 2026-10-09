# Bukal

Before changing code, read:

- [Hackathon plan](docs/hackathon-plan.md) for the first working milestone, schedule, risks, and definition of done.
- [Quiz types and local profile](docs/quiz-types-and-profile.md) for the required five quiz types, local-AI answer evaluation, and yearly activity heatmap.
- [Implementation checklist](docs/implementation-checklist.md) for the ordered tasks and their acceptance checks.
- [SQLite schema](docs/sqlite-schema.md) before implementing Room entities, DAOs, or migrations.
- [Context factory](tools/context-factory/README.md) for the compact implementation constraints and repository guidance.

Follow the narrower instructions in any nearer `AGENTS.md` if one is added later.

Treat the five-question multiple-choice flow as the first working milestone, then follow the documented expanded scope for all five quiz types, local-AI answer evaluation, the local yearly activity profile, and local semantic document search with `ibm-granite/granite-embedding-311m-multilingual-r2`. The embedding model is for search, not answer grading. Do not revive unrelated excluded features. Code, tests, and configuration are authoritative for implemented behavior; update the docs, implementation checklist, and context factory whenever a change alters documented behavior, architecture, scope, or verification steps.

Use Room backed by SQLite for the reviewed six-table structured-data schema, DataStore for small preferences, and app-specific files for downloaded quiz/embedding models and retained source documents. Store overlapping search chunks and their compact vectors in Room. Do not reintroduce JSON-file persistence for attempts, questions, responses, history, profile activity, or the search index.

Before changing code, inspect the relevant files and follow existing patterns. If requirements are ambiguous, state the assumption or ask. After edits, run the closest relevant verification and report what was and was not verified.
