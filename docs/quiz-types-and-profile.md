# Bukal Quiz Types and Local Profile

This document defines the required product expansion after the first working multiple-choice milestone. These features are required project scope, but they do not all have to fit inside the original ten-hour hackathon demonstration.

Bukal remains local-first. Question generation, answer checking, attempt history, and profile activity stay on the device. There is no account, backend, synchronization, or learner-data upload.

## Required Quiz Types

| Quiz type | Learner input | Evaluation method |
|---|---|---|
| Multiple choice | Select one of four options | Deterministic answer index |
| Fill in the blank | Enter the missing word or short phrase | Local-AI evaluation against hidden criteria and source evidence |
| Identification | Enter a term, name, or short answer in the learner's own wording | Local-AI evaluation against hidden criteria and source evidence |
| True or false | Select True or False | Deterministic boolean comparison |
| Explanation | Write a short explanation in the learner's own words | Local-AI evaluation against hidden criteria and source evidence |

The model receives only source material as its generation user message: the selected passage and one focus excerpt copied from that passage. Numbered items are focused individually; unnumbered text falls back to paragraphs, then sentences. It returns no IDs, source IDs, evidence, or explanations. Bukal assigns internal question IDs and links every question to the selected passage locally. Passage grounding reduces unsupported output, but it does not prove that a generated question or evaluation is correct.

## Quiz Configuration

Before generation, the learner selects one to five passages with checkboxes and chooses one or more quiz types. Each passage receives a five-slot target distribution. A newly composed passage quiz contains one to five questions because an individual missing slot is skipped after its two retries fail.

Bukal checks every prior attempt for the passage and builds a distinct question pool by type and normalized prompt, preferring the newest copy of a duplicate. For each requested type, it randomly reuses at most that type's target count. Questions of unselected types do not count toward the five-question cap. Bukal generates only the remaining per-type deficits and includes the reused prompts in duplicate checks.

Distribute the five requested slots as evenly as possible in the learner's selected order. For example, selecting all five types requests one question of each type per passage. Selecting multiple choice and fill in the blank requests three of the first selected type and two of the second. The application must not silently replace a missing or failed type with another type. It continues with the reused and successfully generated slots and reports the failed count.

Every Quiz Setup run copies the chosen reusable question content plus generated top-ups into a fresh unanswered passage attempt, then creates a new quiz-set History item. It never overwrites the prior attempts used as its question pool. Selecting an existing History item is a separate exact-retake flow: it clears in-memory responses, reopens those saved questions without type selection or generation, and updates that same History item after completion.

The quiz setup content includes:

- Selected passage count and source IDs
- Saved-versus-new generation summary
- Quiz-type selector
- Up-to-five-questions-per-passage total
- Short explanation that fill in the blank, identification, and explanation answers are checked locally by AI
- **Start Quiz** action

## Shared Generated-Question Contract

Every accepted question has:

- Non-empty question or instruction
- Type-specific answer data

Bukal assigns the five types before generation. The model returns one question as JSON from each fresh, unsaved session, using the system instruction dedicated to that type. The user message contains the selected passage and one focus excerpt copied from it. Before strict parsing, the app may remove exactly one outer plain or `json` Markdown code fence, but it must reject commentary or other surrounding text. Bukal validates every field deterministically, assigns the type and internal IDs, and links the question to the selected passage. Each invalid question receives up to two corrective retries with a specific validation message. Duplicate retries advance to another focus excerpt; missing-field or malformed-shape retries retain the current focus. The app never invents missing answer data.

## Type-Specific Contracts

### Multiple Choice

- Exactly four non-empty, unique options
- If the model returns more than four unique options, keep the matching answer and the first three distractors in their original order instead of retrying
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

### True or False

- One clear yes-or-no question ending in a question mark that can be judged from the selected passage
- No statement form or open-ended What, Who, Where, When, Why, How, or Which prompt
- A JSON boolean answer generated as `true` or `false`
- Two learner choices displayed as **True** and **False**
- Correctness checked locally without an AI call
- Avoid tricky negative wording and unrelated facts

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

The learner may request one short local-AI hint for the current question. Each hint uses a fresh, unsaved model session and receives only the question, visible choices, relevant source passage, and source ID.

Hints return plain text in the question's language. They must not reveal the correct option, missing term, or write the learner's explanation response. Hints exist only during the active quiz and are not saved in History.

## Quiz Interface Requirements

Use a type-specific answer component while keeping the same question progress and navigation:

- Multiple choice: four selectable options
- Fill in the blank: one short text field
- Identification: one short free-text field
- True or false: two selectable options
- Explanation: one multi-line text field

Do not reveal the correct answer, hidden criteria, or selected source passage before submission when doing so would give away the answer.

For a multi-passage quiz, keep one continuous previous/next flow while showing both the passage position and the question position within that passage. Question and response keys must be namespaced by passage so repeated local IDs such as `q1` cannot overwrite one another.

When the learner submits a quiz containing fill in the blank, identification, or explanation items, show a local **Checking answers...** state while the quiz model evaluates those responses. Multiple choice and true or false should be checked immediately without calling the model.

## Document Flashcards

Passage selection provides a **Flashcards** button for the active material. It opens one read-only card flow containing the currently saved questions from every passage in material order. The front shows the question; tapping the card flips it to its saved answer key, and previous or next navigation returns the next card to its question side. Multiple choice and true or false use the correct option text, open-response types use their reference answer, and quizzes saved by older builds may show their matching pairs. Flashcard review does not generate questions, grade answers, change attempt activity, or require a schema change.

## Results and Evidence

For each item, show:

- Quiz type
- Learner response
- Correct answer or expected mapping when applicable
- Deterministic result or local-AI verdict
- An **Explain** action for AI-evaluated answers, with no explanation generated before it is tapped
- The original passage for that result item

The score summary combines all selected passage quizzes, but every result item must reopen and explain against its own passage. Completion persists all member attempts and their parent quiz set atomically, so History shows one reusable object for the whole selection and Profile counts one activity unit per completed set.

Clearly present AI-evaluated results as local AI decisions rather than guaranteed truth.

## Local Profile and Yearly Activity

Add a **Profile** page inspired by GitHub's yearly contribution view. It is a local learning-activity summary, not an online account page.

### Activity Definition

- One activity unit equals one completed quiz set, whether it contains one passage or five.
- Store both the completion timestamp and the completion local date in `YYYY-MM-DD` form.
- A day is active when at least one quiz was completed on that local date.
- Multiple completed quizzes on the same date increase that day's intensity.
- Opening the app, importing a file, or abandoning a quiz does not count as activity.

### Daily Streak and Streak Pet

- A local calendar date qualifies for the daily streak only after completed quiz sets on that date contain at least **10 questions whose persisted result is `correct`**.
- Distinct correct questions accumulate across completed quiz sets on the same date. A copied or retaken question with the same passage, type, and normalized prompt counts at most once that day. Incorrect, unanswered, partially correct, saved, and abandoned questions do not count.
- Streaks count consecutive qualifying local dates. A streak earned yesterday remains current while today's goal is still in progress, then breaks after a full local date passes without reaching 10 correct answers.
- The floating fire pet exists only while the learner has a current streak. It appears on Home, History, and Profile, can be dragged within the available screen area, snaps and minimizes to the nearest left or right edge when released, automatically minimizes after seven seconds without interaction, and expands from its current edge when tapped.
- The pet grows visually at 3, 7, and 14 consecutive qualifying days. If the streak breaks, the pet disappears; reaching 10 correct answers on a later date starts a new streak and returns the pet.
- Daily correct-answer progress and streak dates are derived from completed quiz sets and their stored question results. Do not add a pet, streak, or daily-counter table.

### Yearly Heatmap

The profile shows:

- Total completed quizzes in the selected year
- A week-by-day heatmap for the selected calendar year
- Month labels and weekday guidance
- A year selector
- A legend from **Less** to **More**
- Current 10-correct daily streak
- Longest 10-correct daily streak
- Local achievement previews for concrete quiz-completion and consistency events
- Progress toward the next quiz-count, streak, or quiz-variety milestone

Use deterministic intensity levels:

- 0 completed quizzes: empty
- 1 completed quiz: level 1
- 2 completed quizzes: level 2
- 3 completed quizzes: level 3
- 4 or more completed quizzes: level 4

The heatmap continues to show completed-quiz activity, while streaks use consecutive dates that reached 10 correct answers. Both are derived from saved local quiz sets and question results; do not maintain a separate cloud activity service.

This activity view measures usage consistency only. Do not describe it as mastery, learning quality, intelligence, or academic performance.

Achievement and milestone state is derived from completed local quiz sets and their member question types without introducing an account, reward currency, or separate profile table. **First Steps** requires one completed set, **Week Builder** requires seven consecutive 10-correct days, and **Quiz Explorer** requires completed sets covering all five quiz types. Quiz-count and streak milestones show progress toward 50 quiz sets in the selected year and a 14-day longest streak.

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
| Passage selection | Material name, prominent **Flashcards** action, scrollable passage previews with source IDs, checkboxes for one to five passages, saved-question status, and a sticky continue action showing the maximum question total. |
| Document flashcards | Material name, passage label, card position, one tap-to-reveal question-and-answer card, previous/next actions, and an empty state when the material has no saved questions. |
| File summary | Learner-triggered child screen that either opens the saved Markdown summary or processes every passage once, then presents a readable overview, key-point bullets, and supporting source IDs. It has no regenerate action. |
| Quiz setup and type selection | Selected-passage count and saved-question summary, five selectable type chips, the per-passage five-question distribution, a short local-AI information box, and a sticky start action. |
| Quiz generation | Centered indeterminate progress, one short status message, and an optional small pencil or spark doodle. Do not show a fabricated percentage. |
| Quiz answering | Compact progress bar, quiz-type label, question, type-specific response control, and fixed previous/next actions. Keep decorative elements away from the answer area. |
| Local-AI answer checking | Simple local-processing state showing that open responses are being checked. Do not imply cloud processing or display fabricated progress. |
| Results and selected source passage | Score summary followed by expandable result cards. The selected passage opens in a bottom sheet or expandable detail so it does not become another permanent navigation destination. |
| Attempt history | Chronological list showing one saved or completed quiz set with its material, passage count, question count, saved/completed date, selected types, latest combined score, highest combined score, or Ready state. Selecting an item starts a whole-set retake with cleared responses. |
| Local profile and yearly activity | Year selector, completed-quiz total, current and longest streak cards, horizontally scrollable yearly heatmap, activity-intensity legend, compact local achievements, and next-milestone progress. |

### Useful Gamification

Gamification should communicate progress and reward task completion without creating a separate reward economy. Use:

- Passage and question progress
- Current-question position
- Subtle completion feedback after submission
- Ten-correct daily streaks
- A floating, edge-minimizing fire pet tied to the 10-correct daily streak
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
- `attempts` stores one prepared quiz per passage, including copied reusable questions and generated top-ups, its quiz model, generation time, status, latest completion date and score, and highest score.
- `quiz_sets` stores one History/Profile object for one prepared ordered quiz run and its combined latest and highest scores.
- `quiz_set_items` links a quiz set to its prepared attempts in passage order.
- `questions` stores generated content, hidden answer data, learner response, result, and points in one row. On-demand explanations are transient.
- `matching_pairs` is retained only so quizzes saved by older builds can still be reopened; new quizzes do not write matching rows.

Keep this eight-table shape focused. Do not add a separate question-bank, response, evaluation, accepted-answer, selected-types, profile, streak, or per-run snapshot table. Save each freshly composed question graph in one transaction. Once all selected quizzes are ready, create its ordered quiz set. First completion and exact History retakes update the parent set and every member graph atomically, replacing learner responses and the latest combined score while retaining the highest combined score. Replace a passage's search chunks in one indexing transaction.

Index quiz-set creation time for History and set completion local date for selected-year totals and heatmap queries. Derive heatmap activity from completed quiz sets and derive streak qualification by counting their `correct` question results per local date rather than maintaining a separate activity database or duplicating daily counters.

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
