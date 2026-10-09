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

1. Let the learner select one to five passages with checkboxes and one or more quiz types.
2. For each passage, distribute five slots across the selected types, randomly reuse distinct matching saved questions up to each type's quota, and sequentially generate only the deficits; continue with the successful subset when individual slots fail.
3. Check multiple choice and true or false deterministically.
4. Evaluate fill in the blank, identification, and explanation locally against their hidden reference answer and the top five source matches.
5. Use only boolean local-AI grading, with `false` whenever the model is unsure, and generate an explanation only on request.
6. Provide short on-demand local hints without revealing answers.
7. Save typed attempts, derive a local yearly activity heatmap, and derive current and longest streaks from consecutive dates with at least 10 correct answers.
8. Search imported documents locally through overlapping chunks embedded with Granite Embedding 311M Multilingual R2.
9. Review all currently saved questions for one material as local tap-to-reveal flashcards without generation or grading.
10. Let the learner explicitly request one source-linked Markdown summary per imported material, persist the first successful result, and reuse it without another model call.

## Current Implementation Status

- The launcher opens the model-setup gate and a single Navigation Compose graph connects the approved quiz flow plus file-level flashcard review.
- Passage selection accepts one to five checked passages and shows saved-question counts. Quiz Setup uses all distinct prior passage questions as a local pool, reuses only matching selected types up to their quotas, and sequentially generates the deficits. Each setup run saves fresh composed attempts and a new quiz set; an exact History retake updates its existing set. Active question IDs are namespaced by passage, and checking/evidence remain passage-specific.
- Passage selection also opens a read-only flashcard page for the active material. It reads the saved per-passage question graphs in passage order, shows the question first, and flips to the stored answer key on tap without AI or new persistence.
- History and Profile reuse `BukalBottomNavigation`. Focused quiz-flow screens from Quiz Setup through Results use their own Back, Close, Cancel, Previous/Next, or Done controls without the root footer.
- The Profile heatmap is horizontally scrollable and uses completed quiz-set activity with `0` through `4+` intensity levels. Streak qualification separately aggregates persisted `correct` question results and requires 10 per local date. Year switching, cross-year streak calculation, achievements, and milestone progress remain local; Home reuses the calculated current streak and refreshes it after quiz completion.
- A transparent fire pet floats over Home, History, and Profile only while that current streak exists. It grows at 3, 7, and 14 qualifying days, shows today's correct-answer progress when tapped, can be dragged within the available screen, snaps and minimizes to the nearest left or right edge on release or after seven seconds, and disappears after a full missed qualifying day. This UI state does not add a pet or streak table.
- Quiz Setup intentionally uses a focused Back action and sticky Start Quiz action without the root footer, matching its approved reference and nested-flow role.
- Reuse `BukalBottomNavigation` for the permanent Home, History, and Profile footer. It exposes `MainDestination` selection and callbacks but does not own a navigation controller.
- Use the compact shared scale on upcoming pages: 28/22/20 sp headings and titles, 16/14 sp body text, 14/12 sp labels, 48 dp actions and touch targets, a 64 dp footer, 20–28 dp content icons, and 12–16 dp card padding.
- The project uses AGP 9 built-in Kotlin, Compose, Material 3, the Compose compiler plugin, and the documented light theme.
- Gradle uses the locally installed Java 21 runtime; do not restore the broken Java 25 Foojay daemon requirement.
- Room 2.8.5 with KSP 2.3.12 is configured. The eight entities use schema version 5, including explicit version 3 to 4 quiz-set backfill and version 4 to 5 optional material-summary migrations. Transaction DAOs, validators, embedding-vector codec, all-attempt passage question-pool lookup, whole-set retake persistence, History loading, one-time summary persistence, and exported schemas are implemented under `data/local` and `ai`.
- Passage selection exposes **File summary**. A successful first request processes every ordered passage with the selected quiz model, accepts concise Markdown directly, reduces long note sets in bounded batches, rejects JSON and unknown source IDs, renders the saved result through Markwon's CommonMark parser, and saves the final Markdown on the material. Later opens read it directly; real-device quality, latency, memory, relaunch, and airplane-mode verification remain pending.
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
- Markwon `4.6.2` for Android-native CommonMark rendering on the Compose summary screen
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

one to five selected passages + selected quiz types
  -> calculate five requested slots per passage
  -> randomly reuse distinct saved questions within each selected type quota
  -> sequentially generate only the missing per-type slots
  -> save fresh composed attempts and a new quiz set
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
  -> yearly completed-quiz heatmap plus 10-correct daily streak and streak pet

active material
  -> saved per-passage questions in passage order
  -> tap-to-reveal flashcards using stored answer keys
```

There is no server-side branch in this flow.

## Data and Validation Contracts

- Assign stable TXT passage IDs such as `TXT-P001`.
- Limit model input to the selected passage, approximately 3,000 to 4,000 characters; do not send the entire document.
- Ask for JSON only and parse it with `kotlinx.serialization`. Remove at most one outer plain or `json` Markdown code fence; reject commentary and all other surrounding text.
- Generate each question in its own fresh, unsaved model session. Send only source material as the user message: the selected passage and one focus excerpt copied from it. Never replay conversation history.
- Model output contains only a non-empty question and valid data for the requested type. Assign the type, question IDs, and source linkage locally.
- Multiple choice requires at least four non-empty unique model options and correct answer text that matches one option. Normalize extras to the matching answer plus the first three distractors, preserve their original order, and derive the internal answer index locally.
- Discourage negative multiple-choice prompts using `NOT` or `EXCEPT` wording, but accept otherwise usable output.
- Fill in the blank accepts a blank of any underscore length or a direct question when it has a hidden reference answer.
- True or false returns one clear yes-or-no question ending in a question mark and a JSON boolean answer, which Bukal maps to the deterministic **True** and **False** choices. Reject statement form and open-ended What, Who, Where, When, Why, How, or Which prompts.
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
- Multi-passage selection is capped at five passages, with five requested slots per passage. Reuse never exceeds a selected type's quota, unselected saved types are ignored, and a failed slot is not silently replaced by another type.
- Namespace active question IDs by passage, evaluate responses against their own passage, and persist the parent quiz set plus every member attempt in one transaction.
- Generate a file summary only after an explicit learner action. Process every ordered passage with the selected quiz model, hierarchically reduce large note sets, validate final source IDs, and save deterministic Markdown only after the complete result succeeds.
- A material's first successful summary is immutable: later opens read `materials.summary_markdown` and never call the model. Cancellation or failure leaves the summary fields null so the learner can retry. Granite retrieval is not a substitute for whole-file coverage.

## Persistence Boundaries

Use the following local storage boundaries from the beginning:

```text
Room / SQLite tables
  materials
  passages
  search chunks
  attempts
  quiz sets
  quiz set items
  questions
  matching pairs

DataStore
  small application preferences

files/
  imported-materials/

external-files/
  models/
```

Save every successfully composed passage quiz and its one-to-five question rows in one Room transaction before answering. Once every selected passage quiz is ready, create a new ordered quiz set over those fresh attempts. The matching-pairs table remains only for quizzes saved by older builds. Checking atomically updates the set and every member attempt to `completed`. Exact History retakes update the same set and child graphs, keep the set `earned_points` as the latest combined score, and retain the maximum in `highest_earned_points`. Store the learner response and result directly on each completed question. Replace one passage's search chunks in a separate indexing transaction.

The optional one-time file summary stays on its `materials` row as Markdown plus the generating quiz-model ID and generation time. The DAO writes all three values only while `summary_markdown` is null; the app constructs the complete Markdown before that atomic update, so partial summaries are never persisted.

The exact version-5 ownership, constraints, indexes, migrations, and delete behavior live in the [SQLite schema](../../docs/sqlite-schema.md) and executable [reference DDL](../../docs/sqlite-schema.sql). Keep the reviewed eight-table shape. Per-passage cache lifecycle remains on `attempts.status`; whole-History lifecycle is represented by `quiz_sets.status`. Do not add response, evaluation, accepted-answer, selected-type, activity-summary, streak, model-catalog, settings, account, summary, or per-question-type tables unless the documented flow changes and the schema is reviewed again.

Keep generative and embedding model packages plus retained original documents outside SQLite. Room stores compact per-chunk vector BLOBs, not whole models or documents. DataStore is not a replacement for Room and must not store attempts, passages, questions, or responses.

Export the Room schema and add explicit migrations after a released schema changes. Use an in-memory Room database for DAO, relationship, transaction, and aggregation tests.

Each saved passage quiz stores its generation timestamp, typed question rows, and answer keys before answering. Each completed member attempt stores learner responses and verdicts, while its parent set stores the whole run's completion date and combined score. Selected types are derived from member question rows. Profile activity is derived with Room queries filtered to completed quiz sets:

- One completed quiz set equals one activity unit, regardless of passage count.
- Day intensity is `0`, `1`, `2`, `3`, or `4+` completed quizzes.
- Distinct correct questions accumulate across completed quiz sets on their completion local date; copies or retakes with the same passage, type, and normalized prompt count once per date.
- A streak date requires at least 10 correct question results; streaks count consecutive qualifying dates.
- App opens, imports, and abandoned quizzes do not count.

## Delivery Order

1. Establish a reproducible runnable Android foundation with Room and DataStore configured.
2. Prove one local inference call on the presentation phone.
3. Add the required navigation shell, including Profile.
4. Define and verify both the quiz-model catalog and the exact Granite embedding artifact.
5. Implement model download, verification, retry, and deletion.
6. Implement TXT import, passage construction, overlapping search chunks, embeddings, and stable source IDs.
7. Add document search plus passage and quiz-type selection.
8. Add user-requested, one-time Markdown file summaries with source-linked whole-file coverage.
9. Implement shared and type-specific question contracts with deterministic tests.
10. Generate, parse, validate, and retry five type-specific slots, then continue with the successful subset and report failed slots.
11. Implement type-specific answer controls and deterministic scoring.
12. Implement local-AI evaluation for fill in the blank, identification, and explanation.
13. Implement results, selected-passage viewing, and on-demand source-grounded explanations.
14. Persist typed attempts and history.
15. Derive and display yearly profile activity and streaks.
16. Test failure cases, relaunch, and airplane-mode use across all required types.

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
- Confirm multiple choice and true or false do not call AI for answer checking.
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
