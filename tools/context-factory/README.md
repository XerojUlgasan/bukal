# Bukal Context Factory

This is the compact implementation context for coding agents. Before making product, scope, or architecture decisions, read the [hackathon plan](../../docs/hackathon-plan.md), [quiz types and local profile requirements](../../docs/quiz-types-and-profile.md), and [implementation checklist](../../docs/implementation-checklist.md). Before implementing Room entities, DAOs, or migrations, also read the reviewed [SQLite schema](../../docs/sqlite-schema.md).

## Current Goal

Build Bukal in two ordered milestones.

The first working milestone lets a learner:

1. Install a model-free APK.
2. Download and verify the required Qwen quiz and Granite embedding `.litertlm` models.
3. Import a UTF-8 TXT learning material.
4. Select a bounded passage.
5. Generate five multiple-choice questions on-device.
6. Answer the questions, see a score, and reopen the selected source passage.
7. Review simple local attempt history.
8. Repeat quiz generation in airplane mode after model setup.

After that flow passes, complete the required product expansion:

1. Let the learner select one or more quiz types.
2. Request five questions distributed across multiple choice, fill in the blank, identification, matching, and explanation; continue with the successful subset when individual slots fail.
3. Check multiple choice and matching deterministically.
4. Evaluate fill in the blank, identification, and explanation locally against their hidden reference answer and the top five source matches.
5. Use only boolean local-AI grading, with `false` whenever the model is unsure, and generate an explanation only on request.
6. Provide short on-demand local hints without revealing answers.
7. Save typed attempts and derive a local yearly activity heatmap plus current and longest active-day streaks.
8. Search imported documents locally through overlapping chunks embedded with Granite Embedding 311M Multilingual R2.

## Current Implementation Status

- The launcher opens the model-setup gate and a single Navigation Compose graph now connects all ten approved mock screens.
- Before generation, Bukal checks Room for a quiz already generated for the selected passage and opens those stored questions instead of running local AI again. New generation sends only the bounded passage text to the selected local quiz model, validates one type-specific response per requested slot, and atomically saves the successful question set. Submission scores multiple choice and matching immediately. Each answered open item embeds a query, retrieves up to five chunks from the selected passage, and starts a fresh local quiz-model evaluation grounded only in those matches. First completion and later retakes atomically update the same quiz graph; History shows its latest previous score and retained highest score.
- History and Profile reuse `BukalBottomNavigation`. Focused quiz-flow screens from Quiz Setup through Results use their own Back, Close, Cancel, Previous/Next, or Done controls without the root footer.
- The Profile heatmap is horizontally scrollable and uses completed-only Room activity with `0` through `4+` intensity levels. Year switching, cross-year streak calculation, achievements, and milestone progress are derived locally from completed attempts and stored quiz types; Home reuses the calculated current streak and refreshes it after quiz completion.
- Quiz Setup intentionally uses a focused Back action and sticky Generate action without the root footer, matching its approved reference and nested-flow role.
- Reuse `BukalBottomNavigation` for the permanent Home, History, and Profile footer. It exposes `MainDestination` selection and callbacks but does not own a navigation controller.
- Use the compact shared scale on upcoming pages: 28/22/20 sp headings and titles, 16/14 sp body text, 14/12 sp labels, 48 dp actions and touch targets, a 64 dp footer, 20–28 dp content icons, and 12–16 dp card padding.
- The project uses AGP 9 built-in Kotlin, Compose, Material 3, the Compose compiler plugin, and the documented light theme.
- Gradle uses the locally installed Java 21 runtime; do not restore the broken Java 25 Foojay daemon requirement.
- Room 2.8.5 with KSP 2.3.12 is configured. The same six entities use schema version 3, with explicit version 1 to 2 and 2 to 3 migrations. Transaction DAOs, validators, embedding-vector codec, one-quiz-per-passage lookup, in-place retake persistence, History loading, and exported schemas are implemented under `data/local` and `ai`.
- The local codec stores embedding vectors as little-endian 32-bit floats and validates BLOB length against the recorded dimensions.
- Home imports TXT, text-based PDF, DOCX, and PPTX through Android's document picker and keeps all imported lessons visible. Originals are retained in `files/imported-materials/`; extraction, normalization, bounded passage creation, and overlapping chunk creation run off the UI thread.
- Import rejects files above 50 MiB and extracted text above 2,000,000 characters. Image-only/scanned PDFs remain unsupported because OCR is excluded.
- Search chunks currently use a replaceable 100-character / at-least-20-character-overlap policy. Word-boundary adjustment can increase overlap but chunks never exceed 100 characters. They are stored with exact offsets and initially null embedding fields. Recalibrate the policy with the real Granite tokenizer before declaring retrieval production-ready.
- Navigation Compose, multi-quiz-model download management, DataStore `1.2.1`, and LiteRT-LM Android `0.18.0` are configured. Serialization, quiz inference, and query retrieval are still pending.
- Compact type-specific system instructions generate one question per fresh session; retrieval-grounded open-answer evaluation and on-demand hints remain separate. Generation allows up to two corrective retries per question, while evaluation allows one. Hint inference remains pending.
- The shared Granite runner now serves both passage indexing and a Profile -> Settings vector-search tester. The tester embeds a phrase, ranks compatible stored chunks with cosine similarity, and shows local source-linked results; the learner-facing Home search field remains pending.
- Model setup is a non-bypassable gate until the fixed Granite embedding model and at least one bundled quiz model pass exact file-size and SHA-256 verification. Qwen 3 Compact is the default quiz download. Gemma 3 1B IT is an optional 584,417,280-byte model with a 6 GB RAM guard and explicit Gemma-terms confirmation; its immutable public-mirror artifact must match the canonical Edge Gallery SHA-256. Gemma 4 E2B IT is an optional 2,583,085,056-byte quiz model and its download is rejected below 8 GB device RAM; Gemma 4 E4B IT is excluded because its 12 GB minimum is too restrictive for the first release. The selected verified quiz-model ID is stored in DataStore; Granite is never selectable. Android `DownloadManager` owns background/interrupted transfer recovery, repeated install actions skip installed or active models, and paused, failed, or corrupted files expose retry/reinstall.
- The Granite `.litertlm` artifact, immutable URL, size, and checksum are pinned. After model verification and after every import, a CPU embedding worker resumes pending chunks without blocking Compose, validates full 768-dimensional normalized output, and stores it through Room. Each failed chunk is attempted twice and remains pending for Home Retry. A 2026-10-09 connected-device test passed one verified Granite embedding and Room persistence on a Xiaomi 23049PCD8G running Android 15 in 3.944 seconds of test runtime; full retrieval and on-demand explanation generation, latency benchmarks, and peak memory remain unverified.

## Non-Negotiable Constraints

- Do not bundle generative or embedding model weights in the APK.
- Do not add a backend, cloud database, account system, synchronization, or upload path.
- Keep learning materials, generated quizzes, settings, and attempts on the device.
- Keep local-AI answer evaluations and profile activity on the device.
- Use Room/SQLite for structured learning data, DataStore for small preferences, and app-specific files for models and retained source documents.
- Internet access is only for model download or update.
- Use the pinned `com.google.ai.edge.litertlm:litertlm-android:0.18.0` runtime for local inference. Recheck its API before upgrading.
- Use the pinned `ibm-granite/granite-embedding-311m-multilingual-r2` `.litertlm` artifact for semantic document search only. Keep full 768-dimensional output until device and retrieval benchmarks justify another supported size.
- Start with CPU execution. GPU support is optional only after the full CPU workflow works.
- TXT is the guaranteed hackathon input. PDF is a stretch goal. DOCX and PPTX are post-hackathon work.
- Do not add OCR, handwriting support, teacher dashboards, multiplayer, public sharing, mastery prediction, review scheduling, or formal-exam claims.
- Treat the Profile page as a local usage summary, not an account, social feed, or mastery dashboard.
- Do not describe AI-evaluated fill in the blank, identification, or explanation answers as guaranteed correct.
- Keep one Android application module. Avoid microservices, dependency-injection frameworks, and unnecessary abstraction layers.

## Intended Stack

- Kotlin
- Jetpack Compose and Material 3
- ViewModel, `StateFlow`, and coroutines
- Navigation Compose
- LiteRT-LM Android
- Granite Embedding 311M Multilingual R2 for local semantic search; pinned `.litertlm` artifact, execution pending verification
- Android `DownloadManager`
- Android Storage Access Framework
- `kotlinx.serialization`
- Room backed by SQLite
- Android DataStore for small preferences
- App-specific file storage for models and retained source documents
- JUnit plus real-device manual testing

## UI/UX Implementation Contract

Build Bukal as a practical Material 3 application with an approximately **80% clean product UI / 20% playful personality** balance. The complete page layouts and visual tokens are defined in [Quiz Types and Local Profile](../../docs/quiz-types-and-profile.md#ui-and-interaction-direction).

Before implementing a screen, inspect its matching image in the [Bukal UI Reference Gallery](../../docs/references/ui/README.md). Treat the gallery as visual guidance and the product documents, code, tests, and configuration as authoritative behavior.

Initial tokens:

```text
background #F8F7F2    surface #FFFFFF
primary    #3559C7    accent  #F2B84B
success    #2E7D5B    error   #C74B50
text       #192033    muted   #667085
outline    #D9DEE8

spacing 4 / 8 / 12 / 16 / 24 / 32 dp
card radius 16 dp    button radius 14 dp
minimum touch target 48 dp
```

- Use the Android system font initially and keep all task text readable.
- Use at most one small notebook-style doodle per normal screen. Reserve stronger illustration for empty or completion states.
- Do not use giant decorative headings, poster-like layouts, glassmorphism, excessive nested cards, gradients that reduce legibility, or mascots that displace task content.
- Use five-question progress, completion feedback, streaks, the yearly heatmap, and small local activity achievements as useful gamification. Do not add coins, XP, levels, leaderboards, arbitrary or mastery-based achievements, social competition, or mastery claims.
- Keep permanent bottom navigation to Home, History, and Profile. Model setup is the first-launch gate and remains reachable later for management.
- Keep `Home -> Passage Selection -> Quiz Setup -> Generating -> Quiz -> Checking when needed -> Results` as the focused quiz path.
- Treat generation and answer checking as transient destinations with honest indeterminate progress. Never fabricate a percentage.
- Open evidence from Results in a bottom sheet or expandable detail rather than a permanent navigation item.
- Prefer the small shared component set `BukalBottomNavigation`, `BukalTopAppBar`, `PrimaryActionButton`, `StatusChip`, `PassageCard`, `QuizTypeChip`, `QuizProgressHeader`, `ResultItemCard`, `EmptyState`, `EvidenceBottomSheet`, and `DoodleAccent`.
- Ensure meaningful accessibility semantics, at least 48 dp touch targets, readable contrast, and a non-color indicator for every important state.

## Critical Flow

```text
bundled model metadata
  -> fixed Granite plus at least one verified quiz model
  -> selected quiz-model ID in DataStore
  -> DownloadManager
  -> app-specific model storage
  -> file-size and SHA-256 verification
  -> LiteRT-LM engine

system file picker
  -> TXT / text-based PDF / DOCX / PPTX extraction
  -> normalized bounded passages
  -> stable source IDs
  -> overlapping search chunks
  -> Granite embedding per chunk
  -> local cosine-similarity search

selected passage + selected quiz types
  -> selected-type counts and minimal shape in the system instruction
  -> passage text only as the user message
  -> one type-specific JSON response per requested slot from fresh unsaved model sessions
  -> common and type-specific validation plus local ID/source assignment
  -> type-aware quiz interface
  -> optional on-demand plain-text hint in a fresh unsaved session
  -> deterministic answer checking OR selected-passage top-5 embedding retrieval
  -> local-AI answer evaluation grounded in the retrieved chunks
  -> results and selected source passage
  -> typed local attempt history
  -> yearly activity heatmap and streaks
```

There is no server-side branch in this flow.

## Data and Validation Contracts

- Assign stable TXT passage IDs such as `TXT-P001`.
- Limit model input to the selected passage, approximately 3,000 to 4,000 characters; do not send the entire document.
- Ask for JSON only and parse it with `kotlinx.serialization`. Remove at most one outer plain or `json` Markdown code fence; reject commentary and all other surrounding text.
- Generate each question in its own fresh, unsaved model session. Send only the selected passage text as the user message and never replay conversation history.
- Model output contains only a non-empty question and valid data for the requested type. Assign the type, question IDs, source linkage, and matching-pair IDs locally.
- Multiple choice has exactly four non-empty unique options and correct answer text that matches one option; derive the internal answer index locally.
- Discourage negative multiple-choice prompts using `NOT` or `EXCEPT` wording, but accept otherwise usable output.
- Fill in the blank accepts a blank of any underscore length or a direct question when it has a hidden reference answer.
- Matching requests three pairs but accepts at least two unique prompts, the same number of unique answers, and a complete expected mapping between stable IDs.
- Identification and explanation have a hidden reference answer.
- The open-answer evaluator handles fill in the blank, identification, and explanation. It marks common explicit non-answers false locally. Otherwise Bukal embeds a query containing the question, learner response, and hidden answer; searches only the selected passage; and gives the local quiz model a labeled plain-text request containing up to five highest-similarity chunks. The model returns only `true` or `false`, using `false` whenever it is unsure.
- On-demand hints return one short plain-text clue, do not reveal the answer, use a fresh unsaved session, and are not persisted.
- The embedding model never grades answers. It embeds stored chunks and queries; the quiz model alone assigns the boolean verdict. A separate retrieval and quiz-model call generates a short source-grounded explanation only after the learner taps **Explain**.
- One passage may have many ordered, overlapping search chunks. Store exact text and offsets with every chunk and one vector per chunk.
- Search only compares vectors produced by the same embedding model ID and dimensions. Start from the model's full 768-dimensional output; use a supported Matryoshka size only after device and retrieval benchmarks justify it.
- Validate local-AI evaluation as exactly `true` or `false`, case-insensitively.
- Retry only the invalid question up to twice with validation errors. If the third attempt fails, skip that slot and continue; fail the flow only when no valid question remains.
- Retry invalid answer-evaluation output once. If it fails again, treat it as `false`.
- Never fabricate missing model-output fields in application code.
- Evidence improves grounding but is not proof that a question is correct; always let the learner inspect the source passage.
- Request five slots distributed as evenly as possible across the learner's selected types. Continue with one to five successful questions and report the failed-slot count.

## Persistence Boundaries

Use the following local storage boundaries from the beginning:

```text
Room / SQLite tables
  materials
  passages
  search chunks
  attempts
  questions
  matching pairs

DataStore
  small application preferences

files/
  imported-materials/

external-files/
  models/
```

Save every successful generated quiz, its one-to-five question rows, and any matching pairs in one Room transaction before answering. Mark it `saved` until checking atomically updates it to `completed`. Later retakes update the same row and child graph, keep `earned_points` as the latest previous score, and retain the maximum in `highest_earned_points`. Store the learner response and result directly on each completed question. Replace one passage's search chunks in a separate indexing transaction.

The exact version-3 ownership, constraints, indexes, migrations, and delete behavior live in the [SQLite schema](../../docs/sqlite-schema.md) and executable [reference DDL](../../docs/sqlite-schema.sql). Keep the reviewed six-table shape. Saved versus completed lifecycle is represented by `attempts.status`; do not add response, evaluation, accepted-answer, selected-type, draft/session, activity-summary, streak, model-catalog, settings, account, or per-question-type tables unless the documented flow changes and the schema is reviewed again.

Keep generative and embedding model packages plus retained original documents outside SQLite. Room stores compact per-chunk vector BLOBs, not whole models or documents. DataStore is not a replacement for Room and must not store attempts, passages, questions, or responses.

Export the Room schema and add explicit migrations after a released schema changes. Use an in-memory Room database for DAO, relationship, transaction, and aggregation tests.

Each saved quiz stores its generation timestamp, typed question rows, and answer keys before answering. Each completed attempt stores its completion timestamp/local date, learner responses, deterministic results or local-AI verdicts, and earned/possible points. Selected types are derived from question rows. Profile activity is derived with Room queries filtered to completed attempts:

- One completed quiz equals one activity unit.
- Day intensity is `0`, `1`, `2`, `3`, or `4+` completed quizzes.
- Streaks count consecutive local dates with at least one completed quiz.
- App opens, imports, and abandoned quizzes do not count.

## Delivery Order

1. Establish a reproducible runnable Android foundation with Room and DataStore configured.
2. Prove one local inference call on the presentation phone.
3. Add the required navigation shell, including Profile.
4. Define and verify both the quiz-model catalog and the exact Granite embedding artifact.
5. Implement model download, verification, retry, and deletion.
6. Implement TXT import, passage construction, overlapping search chunks, embeddings, and stable source IDs.
7. Add document search plus passage and quiz-type selection.
8. Implement shared and type-specific question contracts with deterministic tests.
9. Generate, parse, validate, and retry five type-specific slots, then continue with the successful subset and report failed slots.
10. Implement type-specific answer controls and deterministic scoring.
11. Implement local-AI evaluation for fill in the blank, identification, and explanation.
12. Implement results, selected-passage viewing, and on-demand source-grounded explanations.
13. Persist typed attempts and history.
14. Derive and display yearly profile activity and streaks.
15. Test failure cases, relaunch, and airplane-mode use across all required types.

Attempt PDF, GPU, or other deferred work only after the complete required flow passes.

When time is short during the hackathon, finish the complete MCQ milestone before expanded types or profile work. This changes delivery timing, not the required product scope. Cut polish and stretch formats before any model-management, TXT, selected-source access, or offline behavior.

## Verification Priorities

- Inspect the built APK and confirm it contains no `.litertlm` file.
- Test on the actual presentation phone before relying on a model choice.
- Verify download progress, low-storage handling, size and SHA-256 checks, corrupt-file rejection, retry, and deletion.
- Confirm inference work does not block the UI and engine resources are not initialized twice or leaked.
- Test malformed JSON, wrong question counts, wrong type distributions, and invalid type-specific data.
- Confirm five valid MCQs can complete the flow and that a partial valid set also completes with a failure notice.
- Confirm attempts survive app restart.
- After model installation, enable airplane mode, relaunch, and generate another quiz.
- Confirm every requested quiz type is generated with valid type-specific data and locally linked to the selected passage.
- Confirm multiple choice and matching do not call AI for answer checking.
- Confirm fill in the blank, identification, and explanation retrieve up to five selected-passage chunks before boolean local-AI evaluation, default unsure or invalid output to `false`, and generate explanations only after a tap.
- Confirm Granite chunk/query embeddings run on the presentation phone, use matching model IDs and dimensions, and return source-linked results without network access.
- Confirm typed attempts survive relaunch and restore their results.
- Confirm each attempt and its child rows are committed atomically or fully rolled back.
- Test Room foreign keys, relationship loading, indexes, and schema migrations.
- Test activity aggregation across empty days, multiple attempts in one day, year boundaries, leap years, and streak breaks.
- Confirm Profile works in airplane mode and does not create network traffic.

## Change Discipline

- Inspect nearby code and tests before editing and follow established patterns.
- Prefer the smallest implementation that completes the required user flow.
- Add focused tests for deterministic parsing, passage and overlapping search chunking, vector encoding/filtering, type-specific validation, scoring, storage, activity aggregation, and streak logic.
- Treat real-device LiteRT-LM execution, memory behavior, and lifecycle handling as manual verification unless an actual device run was performed.
- Keep this file, the hackathon plan, quiz/profile requirements, implementation checklist, and root `AGENTS.md` synchronized with intentional scope or architecture changes.
