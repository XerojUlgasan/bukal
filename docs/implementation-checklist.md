# Bukal Implementation Checklist

This checklist divides the first working milestone and required product expansion into small, sequential tasks. Complete and verify one task before starting the next. Do not begin stretch work until all required tasks pass.

The complete product scope and technical decisions remain in the [hackathon plan](hackathon-plan.md) and [Quiz Types and Local Profile](quiz-types-and-profile.md). This checklist tracks implementation; it does not expand that scope.

## Working Rules

- [ ] Work on only one numbered task at a time.
- [ ] Keep every change limited to the current task.
- [ ] Run the task's closest automated verification before marking it complete.
- [ ] Record anything that still requires a physical Android device as manually unverified.
- [ ] Update this checklist and the context factory if implementation changes documented behavior, architecture, scope, or verification steps.
- [ ] Complete the five-question MCQ milestone before adding the expanded quiz types and Profile.
- [ ] Keep PDF, DOCX, and PPTX outside the original MCQ milestone schedule unless the user explicitly reprioritizes them. Import support was explicitly reprioritized on 2026-10-09 while model download work continued.

## Current Implementation Status

- A runnable Compose launcher opens the model-setup gate and connects all ten mock reference screens through one Navigation Compose graph.
- Passage selection supports one to five checked passages. For each passage, Quiz Setup randomly reuses distinct saved questions only within the selected per-type quotas, sequentially generates the deficits, saves a fresh composed attempt and History item, and namespaces in-memory question IDs by passage before presenting one combined answering flow. Exact History retakes remain unchanged. Each answered open item still performs a Granite search against only its own passage.
- History and Profile reuse `BukalBottomNavigation`; the focused Generating, Quiz Answering, AI Checking, and Results screens intentionally omit the root footer.
- Profile reads completed quiz sets and persisted question results from Room. The heatmap still uses completed-set intensity levels `0`, `1`, `2`, `3`, and `4+`, while current and longest streaks require 10 correct answers on each consecutive local date. A transparent fire pet floats on Home, History, and Profile while the streak is current, grows at 3, 7, and 14 days, can be dragged within the available screen, snaps to the nearest side on release, and minimizes after seven seconds.
- The five-question distribution is deterministic in selected-type order, and focused unit tests cover empty, single-type, remainder, and all-five-type cases.
- `BukalBottomNavigation` is the reusable Material 3 footer for the Home, History, and Profile destinations. It exposes typed selection state and callbacks without owning navigation.
- The shared phone-first scale uses compact typography, 48 dp actions, a 64 dp footer, smaller icons, and 12–16 dp card padding while preserving 48 dp minimum touch targets.
- Kotlin uses AGP 9's built-in support. Compose, Material 3, the documented light theme, and the Compose compiler plugin are configured.
- Room 2.8.5 and KSP 2.3.12 are configured. The reviewed eight entities use schema version 4. Quiz Setup creates a fresh attempt and quiz set from reusable matching questions plus generated deficits; exact History retakes update the saved set. History shows each set's latest combined score plus retained highest score. The schema export, explicit migrations, validated transaction DAOs, embedding-vector codec, local unit tests, and an in-memory Room instrumentation test are present.
- Home now opens Android's document picker for TXT, text-based PDF, DOCX, and PPTX. Import copies the original into private app storage, extracts text off the UI thread, saves bounded passages in Room, creates pending overlapping search chunks for later embedding, and keeps every imported lesson visible on Home.
- TXT, DOCX, and PPTX extraction plus normalization, stable passage IDs, bounds, and overlap are covered by local tests. The PDFBox Android extractor has an instrumentation test, but that test still requires an emulator or physical device run.
- Navigation Compose and LiteRT-LM Android `0.18.0` are configured. Serialization and DataStore are not configured yet.
- Compact type-specific system instructions now generate one question per fresh session; retrieval-grounded open-answer evaluation and on-demand hint instructions remain separate. Generation allows up to two corrective retries per question, while answer evaluation allows one; hint inference remains pending.
- Profile now opens Settings, where a developer-facing vector-search tester embeds a phrase with Granite, ranks compatible stored chunks by cosine similarity, and shows source-linked results. The planned learner search field on Home remains pending.
- The model gate now requires both pinned public `.litertlm` files. `DownloadManager` restores active transfers after relaunch; exact size and SHA-256 control readiness; paused, corrupt, or failed downloads can be restarted. Real-device inference, latency, and memory use remain unverified.
- Once both model files are verified, a CPU `EmbeddingEngine` resumes pending chunks in the background. Each chunk gets up to two attempts; only finite, nonzero, L2-normalized 768-dimensional vectors are saved. Failures remain pending and Home exposes Retry. A focused connected-device test passed one verified Granite embedding and Room persistence on a Xiaomi 23049PCD8G running Android 15; full query retrieval, on-demand explanation generation, latency benchmarking, and peak-memory measurement remain pending.
- The Gradle daemon now uses the locally installed Java 21 runtime instead of the broken Java 25 Foojay download.
- LiteRT-LM `0.18.0` was the current Google Maven release when this checklist was written on 2026-10-09. Recheck it before implementing Task 2, then keep the tested version pinned.

## Task 1 — Establish a Runnable Android Foundation

### Implementation

- [x] Resolve the Gradle daemon JDK/toolchain failure.
- [ ] Confirm the permanent application ID and namespace.
- [x] Change the visible application name to **Bukal**.
- [x] Configure Kotlin for the Android application.
- [x] Configure Jetpack Compose and Material 3.
- [x] Add the documented Bukal Material 3 color, spacing, shape, and typography foundation using the system font initially.
- [ ] Add lifecycle, ViewModel, coroutine, and test dependencies needed by the planned stack.
- [x] Configure Room with its code-generation plugin and test dependencies.
- [ ] Add Android DataStore for small application preferences.
- [x] Add a launcher activity and a minimal Bukal screen.
- [ ] Replace or remove generated example tests where appropriate.

### Acceptance and Verification

- [x] `testDebugUnitTest` passes.
- [x] `assembleDebug` passes.
- [x] The debug APK installs on an Android device or emulator.
- [x] Bukal launches without crashing.
- [x] No LiteRT-LM runtime or functional model-management behavior is added in this task.

## Task 2 — Prove LiteRT-LM Inference on the Presentation Phone

### Implementation

- [x] Recheck the official LiteRT-LM Android version and API.
- [x] Pin the chosen LiteRT-LM version instead of using `latest.release`.
- [ ] Manually place one supported `.litertlm` model on the presentation phone.
- [ ] Initialize the CPU engine outside the main thread.
- [ ] Send one small test prompt and display its response.
- [ ] Close the conversation and engine resources correctly.
- [ ] Keep this as a narrow technical proof rather than building the complete interface.

### Acceptance and Verification

- [ ] One inference request succeeds on the actual presentation phone.
- [ ] The interface stays responsive during initialization and inference.
- [ ] Repeating the test does not create duplicate engines or crash the application.
- [ ] Engine resources are released after use.
- [ ] The tested phone, Android version, model file, runtime version, execution backend, and observed memory behavior are recorded.
- [ ] Stop and revise the model/runtime choice if this task fails; do not continue to Task 3.

## Task 3 — Create the Application Flow and Navigation Shell

### Implementation

- [x] Add Navigation Compose.
- [x] Add placeholder destinations for model setup, home/import, passage selection, quiz setup, generation, quiz, answer checking, results/evidence, history, and Profile.
- [x] Use permanent bottom navigation only for Home, History, and Profile.
- [x] Keep model setup as the first-launch gate and generation/checking as transient quiz-flow destinations.
- [x] Define the smallest shared application state needed to move through the flow.
- [x] Keep business logic out of placeholder screens.

### Acceptance and Verification

- [ ] Every required destination can be opened through the intended navigation path.
- [ ] Back navigation behaves predictably.
- [ ] Relaunching the application starts at a safe destination.
- [ ] Navigation tests or focused manual checks cover the required flow.
- [ ] The navigation shell does not expose generation, answer checking, or evidence as permanent bottom-navigation items.

## Task 4 — Define and Verify the Model Catalog

### Implementation

- [x] Add the `ModelDownloadSpec` data contract.
- [x] Add model installation states; runtime loading states remain part of Task 2/inference integration.
- [ ] Verify the pinned Qwen quiz model from Task 2 on the presentation phone.
- [x] Pin the optional Gemma 3 1B IT artifact with exact size/checksum, a Gemma-terms confirmation, and a 6 GB minimum device-memory requirement.
- [ ] Verify Gemma 3 1B IT text inference, structured quiz JSON, latency, and peak memory on the presentation phone before presenting it as compatible.
- [x] Pin the optional Gemma 4 E2B IT LiteRT-LM artifact and enforce its 8 GB minimum device-memory requirement before download.
- [ ] Verify Gemma 4 E2B IT text inference, structured quiz JSON, latency, and peak memory on the presentation phone before presenting it as compatible.
- [x] Record immutable URLs, filenames, exact byte sizes, and SHA-256 checksums for both required artifacts; target-device runtime memory remains pending.
- [x] Define the app-specific model storage location.
- [x] Implement installed-file detection, size checking, and SHA-256 verification as testable logic.
- [x] Support a variable-length bundled quiz-model catalog while keeping exactly one pinned embedding model.
- [x] Add a separate embedding-model spec for `ibm-granite/granite-embedding-311m-multilingual-r2`.
- [x] Pin the tokenizer-containing Granite `.litertlm` artifact with immutable URL, filename, exact size, checksum, and 768-dimensional output contract.
- [ ] Verify the Granite artifact on the presentation phone; do not assume that the desktop PyTorch, ONNX, or OpenVINO examples are Android-ready.

### Acceptance and Verification

- [x] Valid model metadata is accepted.
- [x] Missing, incomplete, incorrectly sized, and checksum-mismatched files are rejected.
- [x] Verification logic has focused unit tests.
- [x] No model weights are added to the repository or APK.
- [ ] One query and one text chunk produce compatible Granite embeddings on the presentation phone.

`GemmaQuizGenerationDeviceTest` now exercises one focused multiple-choice request against the
installed Gemma 4 E2B artifact. On 2026-10-09 the test APK compiled, but the connected Xiaomi phone
blocked its installation with `INSTALL_FAILED_USER_RESTRICTED`, so structured-output verification
did not run and remains unchecked.

## Task 5 — Implement Model Download and Management

### Implementation

- [x] Build the first-launch model setup screen and keep it gated until the fixed embedding model and at least one quiz model are verified.
- [x] Show separate quiz-model and fixed embedding-model sections.
- [x] Check available storage before downloading.
- [x] Reject optional model downloads when the device reports less than the model's minimum RAM.
- [x] Download through Android `DownloadManager`.
- [x] Show download progress and understandable status messages.
- [x] Verify file size and SHA-256 before marking the model ready.
- [x] Reject and clean up incomplete or corrupted downloads.
- [x] Add retry for paused, failed, and corrupt downloads plus minimal model-deletion actions.
- [x] Persist the selected quiz-model ID in DataStore and fall back to a verified installed quiz model when needed.
- [x] Skip verified, actively downloading, and already-enqueued models so repeated setup actions do not duplicate downloads.
- [x] Restore active downloads and verified ready state after application relaunch without a separate download database.

### Acceptance and Verification

- [ ] The state flow covers not installed, downloading, verifying, ready, loading, running, and error states.
- [ ] Low-storage, interrupted-download, and corrupt-file paths show clear recovery actions.
- [ ] A verified model remains ready after relaunch.
- [ ] Deleting the model returns the application to model setup.
- [ ] Downloaded models remain in app-specific storage.
- [x] Unit tests cover multi-model readiness, deterministic selection fallback, and duplicate-download filtering.

## Task 6 — Implement Document Import and Passage Construction

### Implementation

- [x] Open TXT, text-based PDF, DOCX, and PPTX through Android's system file picker.
- [x] Read TXT as strict UTF-8, extract PDF text by page, DOCX body paragraphs, and PPTX slide text. Do not use OCR.
- [x] Normalize line endings and excess whitespace.
- [x] Split text at blank lines and remove empty passages.
- [x] Merge short neighboring paragraphs by filling each bounded passage before starting the next.
- [x] Limit generated passages to at most 3,500 characters.
- [x] Assign stable format-prefixed identifiers such as `TXT-P001`, `PDF-P001`, `DOCX-P001`, and `PPTX-P001`.
- [x] Keep the extraction and chunking logic independent from Compose.
- [x] Reject raw files above 50 MiB and extracted text above 2,000,000 characters before Room insertion.
- [x] Split every passage into ordered overlapping search chunks with exact start and exclusive end offsets.
- [ ] Define chunk size and overlap from the Granite tokenizer, then document the tested values.
- [x] Until tokenizer integration is available, use replaceable 100-character chunks with at least 20-character adjacent overlap; each new row starts pending with null embedding fields.
- [x] Generate and persist one full 768-dimensional embedding per search chunk without blocking the interface.
- [x] Leave failed chunks unembedded, retry each once immediately, and expose a manual retry without creating a jobs table.

### Acceptance and Verification

- [x] Focused local tests cover empty TXT, UTF-8/BOM, line-ending differences, extra whitespace, short and long paragraphs, stable IDs, DOCX paragraphs, and ordered PPTX slides.
- [ ] The application never sends the entire document automatically to the model.
- [x] Unsupported, unreadable, image-only, and empty inputs produce clear errors.
- [x] Imported material is copied to private app-specific storage and is not uploaded.
- [x] Tests prove that adjacent chunks overlap and offsets reproduce the stored passage text. Room already supports one optional embedding per chunk.
- [x] Unit tests prove embedding dimension, finite-value, nonzero, normalization, success, retry, and exhausted-retry behavior; the Room test checks pending rows become searchable after persistence.
- [ ] Run the complete chunk-to-Granite-to-Room path on the presentation phone and record latency and memory behavior.
- [ ] Indexing filters or replaces vectors when the embedding model or dimensions change.

The focused `GraniteEmbeddingIndexerDeviceTest` passed on 2026-10-09 with one chunk, full 768-dimensional output, and Room persistence in 3.944 seconds of total test runtime. It did not measure peak memory or the complete query-to-feedback path, so the broader acceptance item remains unchecked.

## Task 7 — Build Material and Passage Selection Screens

### Implementation

- [x] Connect the Home import action to the system picker and show importing, error, empty, and recent-material states.
- [x] Keep every imported lesson visible and selectable after another file is chosen.
- [x] Display mock bounded passage previews with their source IDs.
- [x] Let the learner select one to five passages with accessible checkboxes.
- [x] Show the reusable saved-question count for each passage.
- [x] Keep the material name visible and provide a sticky continue action after a valid passage is selected.
- [x] Add a **Flashcards** action that opens the active material's saved questions across all passages without running AI.
- [x] Show one question-first card at a time, flip to its saved answer on tap, reset on previous/next, and provide an empty state.
- [x] Add mock quiz setup with selectable multiple choice, fill in the blank, identification, true or false, and explanation types.
- [x] Require at least one selected quiz type before enabling the Start Quiz action.
- [x] Show that Bukal will request five questions and may continue with fewer when individual slots exhaust their retries.
- [x] Explain that identification and explanation are evaluated locally by AI.
- [x] Update the mock information text to include fill in the blank in local-AI evaluation.
- [x] Use selectable type chips and show the resulting deterministic five-question distribution without adding points, coins, or levels.
- [x] Prevent generation when no valid passage is selected.
- [x] Provide a clear way to import another supported lesson file.
- [ ] Add a local document-search field and source-linked result list to Home without creating another permanent navigation destination.

### Acceptance and Verification

- [ ] A learner can import a prepared lesson and choose a passage without seeing raw implementation details.
- [ ] Empty, failed, and successful import states are distinguishable.
- [x] Selected passage IDs passed to the next step match the checked passages in material order.
- [x] Selected quiz types and their deterministic five-question distribution match what the learner requested in the mock UI and focused unit tests.
- [x] Focused unit coverage confirms that every current question type and legacy matching data resolves to its saved answer key.
- [ ] A search query returns relevant chunk previews and opens the correct passage entirely on-device.

## Task 8 — Define Typed Question Contracts and Deterministic Validators

### Implementation

- [x] Add minimal type-specific model-output contracts without a returned type; assign types, question IDs, and source linkage locally.
- [x] Add type-specific models for multiple choice, fill in the blank, identification, true or false, and explanation.
- [x] Parse model output with `kotlinx.serialization`.
- [x] Remove at most one outer plain or `json` Markdown code fence before otherwise strict JSON parsing.
- [x] Validate non-empty, unique question text while preserving the requested type distribution.
- [x] Require at least four unique options, normalize extras to the matching answer plus three distractors, and derive the internal answer index.
- [x] Discourage negative multiple-choice wording without rejecting otherwise usable output.
- [x] Accept fill-in-the-blank questions with any underscore length or a direct question when the hidden reference answer is present.
- [x] Validate hidden reference answers for identification and explanation.
- [x] Require a yes-or-no question plus a JSON boolean answer for true or false, rejecting statement and open-ended prompt forms.
- [x] Define typed learner-response and completed result/evaluation models.
- [x] Return useful validation errors without inventing missing fields.
- [x] Add deterministic scoring for multiple choice and true or false.

### Acceptance and Verification

- [x] Unit tests accept one complete valid question of every type.
- [x] Unit tests reject malformed JSON and invalid common or type-specific fields.
- [x] Unit tests cover one outer JSON fence, source-only focused user input, local ID/source assignment, relaxed blanks, harmless extra fields, and negative multiple-choice wording.
- [x] Fill-in-the-blank validation rejects missing reference answers.
- [x] True-or-false tests cover generation, deterministic choices, persistence validation, and scoring without AI.
- [x] Deterministic scoring tests cover correct, incorrect, unanswered, and complete responses.
- [x] These tests run without loading an AI model.

## Task 9 — Integrate Typed Local Quiz Generation

### Implementation

- [x] Define one compact JSON-only generation system instruction for each quiz type.
- [x] Build each instruction with only its minimal type-specific schema and prior questions to avoid.
- [x] Send only source material as the user message: the selected bounded passage plus one focus excerpt copied from it.
- [x] Focus numbered items individually, fall back to paragraphs or sentences, and advance focus when a retry was rejected as a duplicate.
- [x] Keep malformed-shape retries on the same focus and provide a concise, specific validation problem such as a missing field.
- [x] Request each of the five questions in its own fresh, unsaved model session.
- [x] Keep no conversation history.
- [x] Initialize the selected verified quiz model without blocking the interface.
- [x] Generate and parse each response independently.
- [x] Run the common and type-specific validators from Task 8.
- [x] Retry only the invalid question up to twice with the validation error in the system instruction.
- [x] Request five questions, accept the successful subset, and fail generation only when none are valid.
- [x] Reject responses that silently replace or omit a requested type.
- [x] Show loading, success, validation failure, and runtime failure states.
- [x] Prevent concurrent generation requests and duplicate engine initialization.
- [x] Close each conversation after generation and release the engine when the generation ViewModel is cleared.
- [x] Save every successful full or partial generation and its question graph atomically before opening the answering screen.
- [x] Reuse distinct matching questions up to each selected type's quota and sequentially generate only the missing slots for every selected passage.
- [x] Save each Quiz Setup composition as a fresh attempt and History item without changing exact History-retake behavior.
- [x] Namespace active question IDs by passage so responses cannot collide across reused or generated quizzes.

### Acceptance and Verification

- [ ] A selected passage requests five questions using the requested type distribution and either produces five valid questions or clearly reports a usable partial quiz.
- [ ] Each of the five quiz types can be generated with valid type-specific data.
- [x] Two invalid outputs trigger two corrective retries for that question only.
- [x] A third invalid output skips that slot without discarding other valid questions.
- [x] A partial quiz reports how many question slots failed; all five failed slots produce a clear generation error.
- [x] The generation screen identifies the current question from 1 through 5 while retries remain on the same question number.
- [x] Every accepted question receives a local ID and the selected passage's source ID without asking the model to repeat them.
- [ ] Generation does not freeze the interface.

## Task 10 — Build Type-Specific Quiz Answering

### Implementation

- [x] Show one mock question at a time.
- [x] Show mock progress such as `2 of 5`.
- [x] Show passage and per-passage question position during a multi-passage quiz.
- [x] Keep the mock question and response area visually focused, with no decorative illustration competing with the answer controls.
- [x] Show four selectable options for the mock multiple-choice question.
- [x] Show a short text field for fill in the blank.
- [x] Show a short free-text field for identification.
- [x] Show accessible True and False selection controls.
- [x] Show a multi-line text field for explanation.
- [x] Add mock previous and next controls.
- [x] Keep primary navigation controls reachable with touch targets of at least 48 dp.
- [x] Preserve typed responses while navigating between questions in the active in-memory quiz.
- [x] Check deterministic question types without an AI call.
- [x] Define one compact plain-text system instruction for on-demand hints.
- [ ] Generate a hint only when requested in a fresh, unsaved model session and keep it only for the active quiz.
- [x] Do not reveal hidden answers, criteria, explanations, or evidence on the mock answering screen.
- [x] Prevent accidental duplicate submission.

### Acceptance and Verification

- [ ] The learner can enter and edit a valid response for every supported type.
- [ ] Navigation preserves selected answers.
- [x] Multiple choice and true or false receive the expected deterministic result.
- [x] Deterministic answer checking does not initialize the model.
- [ ] Each quiz type can receive a short hint without revealing its answer, and hints do not appear in History.
- [ ] Screen-state tests or focused manual checks cover rotation or activity recreation where practical.

## Task 11 — Evaluate Open Responses with the Local AI

### Implementation

- [x] Define one compact boolean-only evaluation system instruction for fill in the blank, identification, and explanation.
- [x] Build a labeled plain-text evaluation request that keeps the learner answer distinct from the reference and retrieved source text.
- [x] Mark common explicit non-answers false locally without embedding retrieval or quiz-model inference.
- [x] Build an embedding query from the question, learner response, and hidden reference answer; send only those fields plus the top five compatible chunks from the selected passage to the quiz model.
- [x] Partition multi-passage checking by passage so every open response searches only its own source.
- [x] Use a fresh, unsaved model session for each evaluation and keep no conversation history.
- [x] Accept only `true` or `false`, with `false` required when the model is unsure.
- [x] Keep feedback and explanation generation out of the grading call.
- [x] Validate the evaluation response deterministically.
- [x] Retry invalid evaluation output once.
- [x] Show the real local **Checking answers...** state during evaluation.
- [x] Apply one point for `true` and zero points for `false`.
- [x] Prevent concurrent evaluations and release model resources correctly.

### Acceptance and Verification

- [ ] Reasonable alternative wording can be accepted when supported by the criteria and source.
- [ ] Unsupported or uncertain responses receive `false` rather than fabricated support.
- [x] Invalid first output causes only one corrective retry.
- [x] Invalid second output defaults to `false`.
- [x] AI-evaluated grading stores only the verdict, not generated feedback or evidence.
- [ ] Evaluation works in airplane mode after model setup.
- [x] Granite only retrieves the top matches; the generative quiz model assigns the boolean verdict.

## Task 12 — Build Results and Source Evidence

### Implementation

- [x] Show real overall earned and possible points.
- [x] Show the quiz type and learner response for every item.
- [x] Show real deterministic correctness and local-AI verdicts.
- [x] Show expected answers or mappings where applicable.
- [x] Show an **Explain** action only for answered, AI-evaluated items.
- [x] Generate a short source-grounded explanation only after the learner taps **Explain**.
- [x] Show the original selected passage in a bottom sheet rather than a separate permanent navigation destination.
- [x] Open the correct item-specific passage from combined multi-passage results.

### Acceptance and Verification

- [x] Results calculate deterministic and boolean local-AI outcomes correctly in focused unit tests.
- [x] Focused tests prove that explanation generation does not run during grading and uses only the top five matches when requested.
- [x] Every result can reopen the selected source passage.
- [x] Returning from the passage bottom sheet does not lose quiz results.

## Task 13 — Persist Typed Attempts and Build History

### Implementation

- [x] Implement the entities and relationships from the reviewed [SQLite schema](sqlite-schema.md); keep its reference DDL synchronized with intentional schema changes.
- [x] Keep the six reviewed Room entities; retain matching pairs only for quizzes saved by older builds.
- [x] Define explicit primary keys, foreign keys, cascade behavior, and indexes.
- [x] Index generation timestamps for History and completion timestamps/local dates for completed-only Profile queries.
- [x] Add DAOs for saving and reading materials, attempts, full results, and daily activity counts.
- [x] Save each generated quiz or completed attempt and all child records in one validated Room transaction.
- [x] Store the quiz model ID, completion timestamp/local date, question types, source IDs, learner responses, verdicts, and points; derive selected types from question rows.
- [x] Load current typed questions and legacy matching-pair relationships safely and expose refreshed Room-backed history.
- [x] Add a simple history screen showing saved and completed quizzes; selecting either starts a fresh retake.
- [x] Reuse the existing generated quiz for a passage instead of running local AI again.
- [x] Update the same quiz set and member graphs on retake and show its latest combined score plus retained highest score without adding another History item.
- [x] Persist an ordered quiz set over reusable per-passage rows so a multi-passage flow appears as one History object.
- [x] Keep downloaded models and retained original files outside Room.
- [x] Store only per-chunk embedding vectors in Room, encoded as little-endian floats and validated against their recorded dimensions.
- [x] Export Room schema version 4; retain earlier migrations and add version 3 to 4 for quiz-set tables plus legacy one-item backfill.
- [ ] Add focused in-memory Room tests for DAOs, relationships, transactions, and cascade behavior.

On 2026-10-09, the focused Room database and migration suites passed all four tests on the connected Xiaomi device, covering the version 2 to 3 highest-score migration, saved-to-completed in-place update, retake replacement without a new row, highest-score retention, relationships, activity queries, and cascades. Two final assertions for existing passage-quiz lookup compile, but their rerun was blocked when the phone canceled the test-APK install with `INSTALL_FAILED_USER_RESTRICTED`.

### Acceptance and Verification

- [ ] A mixed-type saved quiz appears immediately after generation, remains after relaunch, and becomes a completed attempt after checking.
- [ ] Selecting a saved or completed History item starts a retake with cleared responses and the stored questions.
- [ ] History and typed results survive application restart.
- [ ] Each passage keeps one generated quiz, while an exact passage selection keeps one quiz-set History item that retakes update in place.
- [ ] A failed child-record write rolls back the complete attempt transaction.
- [ ] Deleting or updating records follows the documented foreign-key behavior.
- [ ] No learner data is uploaded or written outside the local Room database and app-specific storage.

## Task 14 — Build the Local Profile and Yearly Activity

### Implementation

- [x] Add the Profile destination.
- [x] Query daily activity from Room by completed quiz-set local date.
- [x] Show the completed quiz total for the selected year.
- [x] Show a week-by-day yearly heatmap with month and weekday guidance.
- [x] Make the week columns horizontally scrollable on narrow phones instead of shrinking cells below a usable size.
- [x] Support year selection.
- [x] Use tested mock intensity levels for `0`, `1`, `2`, `3`, and `4+` completed quizzes.
- [x] Aggregate distinct persisted `correct` questions by completed quiz-set local date without adding a counter table; copied or retaken questions count once per date.
- [x] Display current and longest streaks derived from dates with at least 10 correct answers.
- [x] Show daily correct-answer progress toward the 10-answer goal.
- [x] Add the transparent floating fire pet to Home, History, and Profile while a current streak exists.
- [x] Grow the pet at 3, 7, and 14 days, let it be dragged within the available screen, snap and minimize it to the nearest edge on release or after seven seconds, and restore it on tap.
- [x] Remove the active pet after a full missed qualifying day and return it when a later date reaches the goal.
- [x] Show a **Less** to **More** intensity legend.
- [x] Explain that activity measures usage consistency, not mastery.
- [x] Keep Profile in the Home/History/Profile bottom navigation and avoid account, social, ranking, or reward-economy UI.
- [x] Add local achievement cards and next-milestone progress.
- [x] Replace mock achievement and milestone states with values derived from completed quiz sets and their stored question types.

### Acceptance and Verification

- [ ] App opens, imports, and abandoned quizzes do not create activity.
- [ ] Multiple completed quizzes on one date increase only that day's intensity.
- [ ] Empty dates, consecutive dates, streak breaks, year boundaries, and leap years are tested.
- [x] Focused unit tests cover the 10-correct threshold and current streak derivation.
- [ ] A focused Room test confirms correct-answer aggregation across multiple completed sets on one local date.
- [ ] Yearly totals and daily counts are produced by focused DAO/query tests.
- [ ] Changing the selected year shows the correct attempts.
- [ ] Profile survives relaunch and works in airplane mode.
- [ ] No account, cloud profile, or network activity is introduced.

## Task 15 — Add One-Time File Summaries

### Implementation

- [x] Start summarization only after the learner chooses **File summary** for an imported material.
- [x] Return the saved summary immediately when the material already has one; never call the model or expose regeneration for a successful summary.
- [x] Summarize every ordered bounded passage with the selected local quiz model rather than using Granite top-match retrieval.
- [x] Reduce long note sets in bounded batches while validating that every final key point cites only supplied passage source IDs.
- [x] Accept Markdown directly from the model, unwrap one optional outer Markdown fence, and reject empty, oversized, JSON-shaped, fenced-code, or unknown-source output.
- [x] Render saved Markdown through Markwon's CommonMark parser so heading levels, emphasis, ordered and unordered lists, quotes, links, and other supported Markdown do not appear as raw syntax.
- [x] Save `summary_markdown`, `summary_model_id`, and `summarized_at_epoch_ms` on the material only when `summary_markdown` is null.
- [x] Add explicit Room migration 4 to 5 and export schema 5.
- [x] Leave all summary fields null after cancellation or failure so the learner may retry.

### Acceptance and Verification

- [x] Focused unit tests cover every-passage processing, optional Markdown fences, four useful passage bullets without retry, source-linked Markdown, deterministic source fallback, and retry after an unknown source ID.
- [x] An instrumentation test proves the first saved summary cannot be replaced by a second write.
- [x] A migration test proves version 4 materials receive nullable summary fields.
- [ ] A short, medium, and long prepared file produce useful summaries on the presentation phone without freezing the interface.
- [ ] English, Filipino, and mixed-language fixtures retain their main language and contain no unsupported claims in manual source review.
- [ ] A saved summary reopens after relaunch and in airplane mode without initializing the local model.

## Task 16 — Complete End-to-End Hardening and Demonstration Verification

### Automated and Package Checks

- [ ] Run all unit tests.
- [ ] Run available instrumentation or UI tests.
- [ ] Run in-memory Room DAO, relationship, transaction, aggregation, and migration tests.
- [ ] Confirm the Room schema export is present and current.
- [ ] Build the release candidate APK.
- [ ] Inspect the APK and confirm that it contains no generative or embedding model weights.
- [ ] Confirm that no backend, analytics upload, account, or synchronization path was introduced.
- [ ] Confirm all primary controls meet the 48 dp minimum touch target and important states are not communicated by color alone.
- [ ] Confirm doodles remain decorative accents and do not obscure, crowd, or replace task content.

### Failure Checks

- [ ] Verify the insufficient-storage message.
- [ ] Verify interrupted-download recovery.
- [ ] Verify size and SHA-256 mismatch rejection.
- [ ] Verify malformed JSON handling and the single-retry limit.
- [ ] Verify malformed local-AI evaluation handling and the single-retry limit.
- [ ] Verify unsure evaluation returns `false` and scores zero out of one.
- [ ] Verify an attempt transaction rolls back completely when a child write fails.
- [ ] Verify both model installations and any active transfers restore correctly after relaunch.
- [ ] Verify model deletion and reinstallation.
- [ ] Verify repeated generation does not leak or duplicate engine resources.

### Final Device Demonstration

- [ ] Install the model-free APK on the presentation phone.
- [ ] Open the model catalog and install the supported model.
- [ ] Import the prepared TXT lesson.
- [ ] Select a bounded passage.
- [ ] Select all five quiz types.
- [ ] Request one question of each type and clearly report any skipped slot after its retries are exhausted.
- [ ] Complete the quiz and confirm its score.
- [ ] Confirm deterministic and local-AI answer evaluation paths.
- [ ] Search a retained document with Granite, open a returned passage, and confirm no network request occurs.
- [ ] Request one file summary, confirm every passage is processed, relaunch, and reopen the same saved Markdown without another model call.
- [ ] Reopen the selected source passage from the results.
- [ ] Confirm the attempt appears after relaunch.
- [ ] Confirm the completed quiz set appears once on the Profile heatmap.
- [ ] Confirm the selected year's total and streak calculations.
- [ ] Enable airplane mode.
- [ ] Close and reopen Bukal.
- [ ] Generate and complete another quiz without internet access.
- [ ] Record the final phone, Android version, APK version, model, runtime version, and verification date.

## Definition of Done

- [ ] All sixteen required tasks are complete.
- [ ] All unchecked required items are either resolved or explicitly documented as blockers.
- [ ] The APK contains no model weights.
- [ ] One supported model is downloadable, verified, and usable on the presentation phone.
- [ ] A learner can import TXT, select a passage and quiz types, generate five validated typed questions, complete them, reopen the selected passage, and review local history.
- [ ] Fill in the blank, identification, and explanation responses receive top-five-grounded boolean local-AI verdicts, with explanations generated only on request.
- [ ] Profile correctly shows yearly completed-quiz activity and 10-correct daily streaks, and the floating pet follows that same streak.
- [ ] Overlapping chunks are embedded with Granite and searchable locally using compatible model IDs and dimensions.
- [ ] Each imported material can receive at most one user-requested Markdown summary, which is stored locally and reused without regeneration.
- [ ] Structured learning data uses Room/SQLite, preferences use DataStore, and large files remain outside the database.
- [ ] The same workflow succeeds in airplane mode after model installation.

## Deferred Backlog

Do not begin these before the sixteen required tasks pass:

- [ ] Text-based PDF import
- [ ] GPU acceleration
- [ ] DOCX import
- [ ] PPTX import
- [ ] Filipino-language evaluation
- [ ] Multiple-document library
- [ ] Full quiz editing
- [ ] Production-level download recovery
- [ ] Wider device testing
