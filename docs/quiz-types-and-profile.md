# Bukal Quiz Types and Local Profile

This document defines the required product expansion after the first working multiple-choice milestone. These features are required project scope, but they do not all have to fit inside the original ten-hour hackathon demonstration.

Bukal remains local-first. Question generation, answer checking, attempt history, and profile activity stay on the device. There is no account, backend, synchronization, or learner-data upload.

## Required Quiz Types

| Quiz type | Learner input | Evaluation method |
|---|---|---|
| Multiple choice | Select one of four options | Deterministic answer index |
| Fill in the blank | Enter the missing word or short phrase | Local-AI evaluation against hidden criteria and source evidence |
| Identification | Enter a term, name, or short answer in the learner's own wording | Local-AI evaluation against hidden criteria and source evidence |
| Matching | Connect each prompt with its corresponding answer | Deterministic expected-pair comparison |
| Explanation | Write a short explanation in the learner's own words | Local-AI evaluation against hidden criteria and source evidence |

The model receives only the selected passage as its user message. It returns no IDs, source IDs, evidence, or explanations. Bukal assigns internal question IDs and links every question to the selected passage locally. Passage grounding reduces unsupported output, but it does not prove that a generated question or evaluation is correct.

## Quiz Configuration

Before generation, the learner chooses one or more quiz types. Bukal requests five questions. A completed generation contains one to five questions because an individual slot is skipped after its two retries fail.

When more than one type is selected, distribute the five requested slots as evenly as possible in the learner's selected order. For example, selecting all five types requests one question of each type. The application must not silently replace a failed type with another type. It continues with the successful slots and reports the failed count.

The quiz setup content includes:

- Selected passage and source ID
- Quiz-type selector
- Up-to-five-question total
- Short explanation that fill in the blank, identification, and explanation answers are checked locally by AI
- **Generate Quiz** action

## Shared Generated-Question Contract

Every accepted question has:

- Non-empty question or instruction
- Type-specific answer data

Bukal assigns the five types before generation. The model returns one question as JSON from each fresh, unsaved session, using the system instruction dedicated to that type. The user message contains only the selected passage text. Before strict parsing, the app may remove exactly one outer plain or `json` Markdown code fence, but it must reject commentary or other surrounding text. Bukal validates every field deterministically, assigns the type and internal IDs, and links the question to the selected passage. Each invalid question receives up to two corrective retries before the app shows a clear error instead of inventing missing answer data.

## Type-Specific Contracts

### Multiple Choice

- Exactly four non-empty, unique options
- Correct answer text that exactly matches one option; Bukal derives the internal answer index
- One selected option from the learner
- Correctness checked locally without an AI call
- The prompt still discourages negative `NOT` or `EXCEPT` wording, but validation does not reject an otherwise usable question

### Fill in the Blank

- Sentence or direct short-answer prompt; a blank may use one or more underscores but is not required
- Hidden reference answer
- Learner enters a short text response
- Granite retrieves the most relevant selected-passage chunks, then local AI evaluates the response against the reference answer and those matches

### Identification

- Prompt asking for a term, person, concept, or short answer
- Hidden reference answer
- Learner enters a short free-text response
- Granite retrieves the most relevant selected-passage chunks, then local AI evaluates reasonable wording variations against the reference answer and those matches

### Matching

- At least two unique left-side prompts; generation requests three
- The same number of unique right-side answers
- A deterministic expected mapping between their stable IDs
- Right-side answers may be shuffled for display
- A matching question receives full credit only when all pairs are correct; the result view may still show which individual pairs were wrong

### Explanation

- Prompt requiring a short explanation rather than one exact phrase
- Hidden reference answer
- Learner enters a free-text response
- Granite retrieves the most relevant selected-passage chunks, then local AI evaluates the response against the reference answer and those matches

## Local-AI Answer Evaluation

Fill in the blank, identification, and explanation do not use plain string equality. The local quiz model receives only:

- The question
- The learner's response
- The hidden reference answer
- Up to five highest-similarity indexed chunks from the selected source passage, including their source ID

The retrieval query combines the question, learner response, and hidden reference answer. Search is restricted to the selected passage and uses the same Granite model and vector dimensions as indexing. When fewer than five indexed chunks exist, all available matching chunks are used. The quiz model must use only these retrieved matches as factual evidence.

Obvious non-answers such as `idk`, `I don't know`, and `hindi ko alam` are marked false locally without loading either model. Other grading requests use labeled plain text, not JSON. They place the question, reference answer, retrieved matches, and learner answer in distinct sections, then ask whether the learner answer itself is correct. The evaluator must return only `true` or `false`, without JSON or extra text. `true` means correct. `false` means incorrect and is also required whenever the model is unsure. The application accepts the response case-insensitively, retries an invalid response once, and defaults to `false` if the second response is still invalid.

The Granite embedding model is not an answer evaluator. It only retrieves the evidence candidates; the selected local quiz model makes the boolean decision.

For self-study scoring:

- `true` = correct = 1 point
- `false` = incorrect = 0 points

Grading does not generate feedback. Results show the verdict and original passage. Only when the learner taps **Explain** does Bukal perform another top-five retrieval and ask the local quiz model for a short source-grounded reason why the answer was accepted or rejected. This explanation is transient and is not saved with the attempt. Bukal must not claim that this grading is suitable for formal examinations.

## On-Demand Hints

The learner may request one short local-AI hint for the current question. Each hint uses a fresh, unsaved model session and receives only the question, visible choices or matching items, relevant source passage, and source ID.

Hints return plain text in the question's language. They must not reveal the correct option, missing term, complete matching pair, or write the learner's explanation response. Hints exist only during the active quiz and are not saved in History.

## Quiz Interface Requirements

Use a type-specific answer component while keeping the same question progress and navigation:

- Multiple choice: four selectable options
- Fill in the blank: one short text field
- Identification: one short free-text field
- Matching: pair-selection or equivalent accessible matching controls
- Explanation: one multi-line text field

Do not reveal the correct answer, hidden criteria, or selected source passage before submission when doing so would give away the answer.

When the learner submits a quiz containing fill in the blank, identification, or explanation items, show a local **Checking answers...** state while the quiz model evaluates those responses. Multiple choice and matching should be checked immediately without calling the model.

## Results and Evidence

For each item, show:

- Quiz type
- Learner response
- Correct answer or expected mapping when applicable
- Deterministic result or local-AI verdict
- An **Explain** action for AI-evaluated answers, with no explanation generated before it is tapped
- The original selected passage

Clearly present AI-evaluated results as local AI decisions rather than guaranteed truth.

## Local Profile and Yearly Activity

Add a **Profile** page inspired by GitHub's yearly contribution view. It is a local learning-activity summary, not an online account page.

### Activity Definition

- One activity unit equals one completed quiz attempt.
- Store both the completion timestamp and the completion local date in `YYYY-MM-DD` form.
- A day is active when at least one quiz was completed on that local date.
- Multiple completed quizzes on the same date increase that day's intensity.
- Opening the app, importing a file, or abandoning a quiz does not count as activity.

### Yearly Heatmap

The profile shows:

- Total completed quizzes in the selected year
- A week-by-day heatmap for the selected calendar year
- Month labels and weekday guidance
- A year selector
- A legend from **Less** to **More**
- Current active-day streak
- Longest active-day streak
- Local achievement previews for concrete quiz-completion and consistency events
- Progress toward the next quiz-count, streak, or quiz-variety milestone

Use deterministic intensity levels:

- 0 completed quizzes: empty
- 1 completed quiz: level 1
- 2 completed quizzes: level 2
- 3 completed quizzes: level 3
- 4 or more completed quizzes: level 4

Streaks count consecutive local calendar dates with at least one completed quiz. The heatmap and streaks are derived from saved attempts; do not maintain a separate cloud activity service.

This activity view measures usage consistency only. Do not describe it as mastery, learning quality, intelligence, or academic performance.

Achievement and milestone state is derived from completed local attempts and their stored question types without introducing an account, reward currency, or separate profile table. **First Steps** requires one completed quiz, **Week Builder** requires a seven-day longest streak, and **Quiz Explorer** requires completed attempts covering all five quiz types. Quiz-count and streak milestones show progress toward 50 quizzes in the selected year and a 14-day longest streak.

## Local Semantic Document Search

The learner can search retained imported documents by meaning, not only exact words. Search stays on-device:

1. Split every stored passage into ordered, overlapping chunks.
2. Generate one embedding per chunk with `ibm-granite/granite-embedding-311m-multilingual-r2`.
3. Embed the learner's query with the same model and dimensions.
4. Rank compatible chunks by cosine similarity and show their material, source ID, and text preview.
5. Open the original passage from a result.

The full document is not copied into an embedding row. One document can have many passages, each passage can have many overlapping search chunks, and each chunk has its own vector. The chunk text and offsets are retained so results remain inspectable.

The selected Granite model has a 768-dimensional full output and supports smaller Matryoshka dimensions. Keep the database dimension-aware, but choose an output size only after retrieval-quality and real-device benchmarks. The Android runtime package, tokenizer, exact file, byte size, and checksum must be pinned and verified before implementation. Do not bundle the model in the APK.

Semantic retrieval finds relevant source text; it does not grade quiz answers and does not prove a passage is correct. The same retrieval path supplies up to five selected-passage chunks to the local quiz model for boolean grading and, separately, for an on-demand explanation.

## Required Pages

The expanded application requires these destinations:

1. Model setup and management
2. Home, material import, and local document search
3. Passage selection
4. Quiz setup and type selection
5. Quiz generation
6. Quiz answering
7. Local-AI answer checking when needed
8. Results and selected source passage
9. Attempt history
10. Local profile and yearly activity

The generation and answer-checking destinations may be transient states rather than permanent bottom-navigation items.

## UI and Interaction Direction

Bukal should feel like a useful learning application first and a playful product second. Use an approximately **80% clean product UI / 20% playful personality** balance. Build the interface with practical Material 3 layouts, then add small notebook-style doodles only where they support orientation, empty states, loading, or completion feedback.

Use the [Bukal UI Reference Gallery](references/ui/README.md) as the visual companion to this contract. The screenshots demonstrate hierarchy and component direction; this document remains authoritative for behavior and content requirements.

### Visual Foundation

Use this initial token set consistently:

```text
Background:  #F8F7F2
Surface:     #FFFFFF
Primary:     #3559C7
Accent:      #F2B84B
Success:     #2E7D5B
Error:       #C74B50
Text:        #192033
Muted text:  #667085
Outline:     #D9DEE8

Spacing:     4, 8, 12, 16, 24, 32 dp
Card radius: 16 dp
Button radius: 14 dp
Touch target: at least 48 dp
```

Use the Android system font initially. Keep normal interface and body text highly readable. A custom display font may be considered later only if it does not reduce readability or delay the working flow.

Use a compact phone-first scale for a 360 dp-wide layout: 28 sp display headings, 22 sp section headings, 20 sp page titles, 16/14 sp body text, and 14/12 sp labels. Keep normal actions at 48 dp high, the permanent bottom navigation near 64 dp high, content icons primarily between 20 and 28 dp, and ordinary card padding between 12 and 16 dp. Preserve the 48 dp minimum touch target even when the visible icon is smaller.

Do not turn screens into illustrated posters. Avoid giant decorative headings, gradients that reduce legibility, glassmorphism, excessive nested cards, sticker clutter, or mascots that consume the main task area. Limit each screen to at most one small decorative doodle or illustration unless it is an empty or completion state.

### Page Layout Contract

| Page | Practical layout |
|---|---|
| Model setup and management | Standard top bar, short explanation, a variable list of quiz-model cards, one fixed embedding-model card, download size, verified status, quiz-model selection, and install/retry/remove actions. The screen cannot be bypassed until Granite and at least one quiz model pass size and SHA-256 verification. |
| Home and material import | App bar with compact quiz/embedding model status, local document search with source-linked results, one primary import card, all retained imported lessons, and bottom navigation for Home, History, and Profile. |
| Passage selection | Material name, scrollable passage previews with source IDs, a single-selection control, and a sticky continue action. |
| Quiz setup and type selection | Selected-passage summary, five selectable type chips, the five-question distribution, a short local-AI information box, and a sticky generate action. |
| Quiz generation | Centered indeterminate progress, one short status message, and an optional small pencil or spark doodle. Do not show a fabricated percentage. |
| Quiz answering | Compact progress bar, quiz-type label, question, type-specific response control, and fixed previous/next actions. Keep decorative elements away from the answer area. |
| Local-AI answer checking | Simple local-processing state showing that open responses are being checked. Do not imply cloud processing or display fabricated progress. |
| Results and selected source passage | Score summary followed by expandable result cards. The selected passage opens in a bottom sheet or expandable detail so it does not become another permanent navigation destination. |
| Attempt history | Chronological list showing one saved or completed quiz per passage with its material, saved/completed date, selected types, latest previous score, highest score, or Ready state. Selecting an item starts a retake with cleared responses. |
| Local profile and yearly activity | Year selector, completed-quiz total, current and longest streak cards, horizontally scrollable yearly heatmap, activity-intensity legend, compact local achievements, and next-milestone progress. |

### Useful Gamification

Gamification should communicate progress and reward task completion without creating a separate reward economy. Use:

- Five-question progress
- Current-question position
- Subtle completion feedback after submission
- Completed-quiz activity streaks
- Yearly activity heatmap
- Small local achievements tied to completed activity
- Progress toward explicit quiz-count, streak, and quiz-variety milestones
- Short encouraging result messages
- Small star, spark, book, or pencil doodles in success and empty states

Do not add coins, experience points, levels, leaderboards, arbitrary or mastery-based achievements, constant confetti, social competition, or mastery claims.

### Navigation and Reusable UI

Use three permanent bottom-navigation destinations:

```text
Home
History
Profile
```

Model setup is a first-launch gate and remains reachable later as model management. The quiz flow is a focused nested path:

```text
Home
  -> Passage Selection
  -> Quiz Setup
  -> Generating
  -> Quiz
  -> Checking when required
  -> Results
```

Prefer a small reusable component set rather than page-specific abstractions:

```text
BukalTopAppBar
BukalBottomNavigation
PrimaryActionButton
StatusChip
PassageCard
QuizTypeChip
QuizProgressHeader
ResultItemCard
EmptyState
EvidenceBottomSheet
DoodleAccent
```

All components must preserve readable contrast, support at least 48 dp touch targets, and expose meaningful accessibility semantics. Visual state must never be communicated through color alone.

## Persistence

Use Room, backed by the device's local SQLite database, for structured document, search, quiz, and activity data.

Use the reviewed [SQLite schema](sqlite-schema.md) as the entity, relationship, constraint, index, migration, and delete-policy contract.

- `materials` stores imported-document metadata.
- `passages` stores bounded extracted source text.
- `search_chunks` stores overlapping text, offsets, and one optional embedding per chunk.
- `attempts` stores one saved generated quiz per passage, its quiz model, generation time, status, latest completion date and score, and highest score.
- `questions` stores generated content, hidden answer data, learner response, result, and points in one row. On-demand explanations are transient.
- `matching_pairs` stores expected matching pairs and learner selections.

Keep this six-table shape flat. Do not add separate draft, quiz, one-to-one response or evaluation, accepted-answer, selected-types, profile, or streak tables. Save each generated question graph in one transaction. First completion and later retakes update that same graph atomically, replacing learner responses and the latest score while retaining the highest score. Replace a passage's search chunks in one indexing transaction.

Index generation time for History and completion local date for selected-year totals and heatmap queries. Derive activity and streaks only from attempts whose status is `completed` rather than maintaining a separate activity database or duplicating daily counters.

Use DataStore only for small application settings, including the selected quiz-model ID. Quiz-model and fixed embedding-model identities come from the bundled catalog, while downloaded models and retained original material files stay in app-specific file storage; do not store whole model or document files in SQLite. Granite is fixed and is never changed by the quiz-model selection.

Room schema migrations are required after a released schema changes. DAO, relationship, transaction, date-grouping, streak-input, chunk-overlap, vector encoding, and model/dimension filtering need focused tests.

## Non-Goals

- No cloud profile or authentication
- No social activity feed or public streak sharing
- No teacher dashboard
- No formal examination grading claims
- No mastery prediction
- No review scheduling
- No reward economy required for the activity heatmap
