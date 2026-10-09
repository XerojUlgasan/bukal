# Bukal SQLite Schema

The executable reference DDL is [`sqlite-schema.sql`](sqlite-schema.sql). It uses eight tables. Retained documents and model files remain in app-specific storage, while small preferences remain in DataStore.

This contract is implemented by the Room entities, DAOs, transactions, and validators under `app/src/main/java/com/example/bukal/data/local/`. Room exports schemas 1 through 5 under `app/schemas/com.example.bukal.data.local.BukalDatabase/`; migration 3 to 4 adds quiz sets and backfills every legacy attempt as a one-item set, while migration 4 to 5 adds the optional one-time Markdown summary fields to `materials`.

## Supported Flow

```text
document file
  -> material metadata
  -> bounded passages
  -> overlapping search chunks
  -> one embedding per chunk
  -> semantic document search

user requests file summary
  -> summarize every ordered passage with the selected local quiz model
  -> reduce the source-linked notes into one validated Markdown summary
  -> save it once on the material row
  -> reopen the stored summary without another model call

one to five selected passages
  -> calculate five selected-type slots per passage
  -> randomly reuse distinct matching questions from prior attempts
  -> sequentially generate only missing per-type slots
  -> save fresh composed attempts and link them into a new quiz set
  -> answers and results saved with each question in one set transaction
  -> one History item
  -> yearly heatmap activity derived from completed quiz sets
  -> daily streak qualification derived from their correct question results
```

Learner responses stay in memory while answering. For each selected passage, Bukal reads all prior questions, keeps the newest copy of each type-and-prompt duplicate, randomly chooses matching questions up to the requested type quotas, and generates only the deficits. It copies that composition into a fresh `saved` attempt in one transaction, preserving every prior attempt. Once all selected passage attempts are ready, Bukal creates a new ordered `quiz_sets` row. Checking updates the set and every member attempt in one transaction. An exact History retake replaces that set's latest responses and combined score, retains its highest score, and does not create another row.

## The Eight Tables

| Table | What it stores |
|---|---|
| `materials` | Imported document name, format, optional retained-file path, import time, and an optional one-time Markdown summary with its quiz-model ID and generation time. The actual document is not stored as a database BLOB. |
| `passages` | Ordered, bounded source text extracted from a material. Quiz evidence points back to a passage's stable `source_id`. |
| `search_chunks` | Overlapping pieces of a passage. Every chunk has its own optional embedding, so one document can produce many searchable vectors. |
| `attempts` | One prepared quiz per passage, containing copied reusable questions and generated top-ups plus its quiz model ID, generation time, status, latest completion time/local date, latest score, highest score, and possible points. |
| `quiz_sets` | One saved or completed History object for one prepared ordered quiz run, including its combined latest and highest scores. |
| `quiz_set_items` | Ordered links from a quiz set to its prepared per-passage attempts. |
| `questions` | The generated question, type-specific answer key, learner response, boolean-derived result, and score. |
| `matching_pairs` | Legacy matching data retained only so quizzes saved by older builds can still be reopened. New quizzes do not write rows here. |

Because schema version 1 made attempt completion time/date non-null, a saved attempt uses its generation time/date as temporary values. Attempt status distinguishes cached unanswered graphs from completed member graphs; `quiz_sets.status` is authoritative for History and Profile.

## Relationship Map

```mermaid
erDiagram
    MATERIALS ||--o{ PASSAGES : contains
    PASSAGES ||--o{ SEARCH_CHUNKS : splits_into
    PASSAGES ||--o{ ATTEMPTS : used_for
    QUIZ_SETS ||--|{ QUIZ_SET_ITEMS : orders
    ATTEMPTS ||--o{ QUIZ_SET_ITEMS : included_in
    ATTEMPTS ||--|{ QUESTIONS : contains
    QUESTIONS ||--o{ MATCHING_PAIRS : may_contain
```

## Why the Embedding Is on `search_chunks`

The full document remains in its retained file, and each passage keeps only bounded extracted text. A passage is then split into multiple overlapping chunks. Each `search_chunks` row stores:

- `passage_id` and `chunk_index` for ordering;
- `start_offset` and exclusive `end_offset` for traceability;
- the exact chunk `content` sent to the embedding model;
- `embedding_model_id`, `embedding_dimensions`, and the vector BLOB.

The embedding fields are nullable as one group. A row with all three fields null is waiting to be indexed; a row with all three populated is searchable. This makes failed or interrupted indexing retryable without a jobs table.

The selected model is `ibm-granite/granite-embedding-311m-multilingual-r2`. Its full output has 768 dimensions and the model also supports smaller Matryoshka dimensions. The first Android implementation must benchmark retrieval quality, latency, memory use, runtime format, and device compatibility before choosing anything smaller than the full output. The chosen dimension is stored on every chunk so incompatible vectors are never compared accidentally.

Chunk size and overlap are application constants, not database columns. They must be selected using the model tokenizer and verified on representative TXT, PDF, DOCX, and PPTX imports. Re-indexing replaces the chunks for a passage in one transaction.

Until Granite tokenizer benchmarks select the final policy, import creates provisional 100-character chunks with at least 20-character adjacent overlap. Word-boundary adjustment may increase the overlap but never lets a chunk exceed 100 characters. Their exact offsets and text enter Room with all three embedding fields null. The background indexer uses LiteRT-LM on the CPU and writes all three fields only after a vector is confirmed to be finite, nonzero, L2-normalized, and exactly 768 dimensions. A failed chunk is retried once immediately and otherwise stays null for a later manual or startup retry. Once benchmarks choose the final chunk policy, re-index each passage transactionally before regenerating vectors; the schema does not need to change.

For the first local implementation, load vectors produced by the active model and calculate cosine similarity in Kotlin. Add a SQLite vector extension only if measured document sizes make this too slow.

## Question and Evaluation Shape

The five current quiz types share one `questions` table:

- Multiple choice uses `option_0` through `option_3` and `correct_option_index`.
- True or false uses `option_0 = True`, `option_1 = False`, and `correct_option_index`; the remaining option columns stay null.
- Fill in the blank, identification, and explanation use `reference_answer`. The nullable `grading_criteria` column remains in the reviewed version-1 schema but is not required by the current generator or evaluator. The local generative quiz model evaluates the learner's `text_response`.
- Legacy matching questions use child `matching_pairs` rows. Each expected pair also holds `selected_right_id` after the learner answers.

`selected_option_index`, `text_response`, `result`, and points are stored directly on the question. The version-1 `ai_feedback` and `evaluation_evidence` columns remain nullable for schema compatibility but current boolean grading leaves them null; on-demand explanations are transient and are not persisted.

The embedding model performs semantic retrieval and does not grade answers. For open-answer grading, it retrieves up to five chunks from the selected passage; the local generative quiz model uses those matches to return only `true` or `false`, with `false` when unsure. A separate top-five retrieval and plain-text model call happens only when the learner requests an explanation. Multiple choice and true or false remain deterministic.

## Profile, Heatmap, and Streak Pet

Profile data is derived only from completed quiz sets; merely generating or saving passage quizzes does not create activity:

```sql
SELECT completed_local_date, COUNT(*) AS completed_count
FROM quiz_sets
WHERE status = 'completed'
  AND completed_local_date BETWEEN :year || '-01-01' AND :year || '-12-31'
GROUP BY completed_local_date
ORDER BY completed_local_date;
```

The yearly heatmap uses these daily completed-set counts. The pet and Profile share a stricter daily-streak query:

```sql
SELECT
    quiz_sets.completed_local_date,
    COUNT(
        DISTINCT CASE WHEN questions.result = 'correct' THEN
            attempts.passage_id || CHAR(31) || questions.quiz_type ||
            CHAR(31) || LOWER(TRIM(questions.prompt))
        END
    ) AS correct_count
FROM quiz_sets
JOIN quiz_set_items ON quiz_set_items.quiz_set_id = quiz_sets.id
JOIN attempts ON attempts.id = quiz_set_items.attempt_id
JOIN questions ON questions.attempt_id = quiz_set_items.attempt_id
WHERE quiz_sets.status = 'completed'
GROUP BY quiz_sets.completed_local_date
ORDER BY quiz_sets.completed_local_date;
```

The distinct key is the source passage plus question type plus normalized prompt, so copied or retaken questions cannot be farmed repeatedly on one date. Kotlin keeps only dates with at least 10 correct answers, then calculates current and longest consecutive-date streaks. Yesterday's qualifying date remains current while today's goal is in progress; the streak breaks after a full missed local date. The floating pet reads that same derived state, so a profile, pet, activity, daily-total, or streak table would duplicate data and could drift out of sync.

## Data Kept Outside SQLite

| Storage | Data |
|---|---|
| Room/SQLite | The eight structured tables above. |
| DataStore | Selected quiz model ID, selected embedding model ID, and other small preferences. |
| App-specific files | Original retained documents, the local quiz model, and the Granite embedding model package. |

Neither model belongs in the APK or in SQLite. Download metadata must pin the exact artifact, runtime format, byte size, and SHA-256 checksum before implementation.

## Rules Enforced by the App

The executable reference DDL demonstrates the complete SQLite constraints. Room annotations enforce keys, parent-child ownership, nullability, uniqueness, and indexes; `DatabaseValidation` mirrors the remaining enum, range, embedding, evidence, type-shape, score, and cross-row checks before protected insert operations run:

1. A saved quiz or completed attempt contains one to five questions with contiguous positions. Saved rows contain only unanswered questions and no learner responses.
2. Every question's evidence is an exact substring of the selected passage.
3. Multiple-choice options are distinct, true-or-false rows contain exactly the two fixed choices, and deterministic answers agree with their answer keys.
4. Fill-in-the-blank, identification, and explanation evaluations save only correct, incorrect, or unanswered results and do not store generated feedback.
5. Legacy matching data requires at least two complete pairs, and every selected right ID belongs to that question.
6. Question points sum to the attempt totals. Correct answers earn one point; incorrect and unanswered answers earn zero out of one possible point.
7. On a `completed` row, `completed_local_date` is the real device-local calendar date captured when the attempt completes. Saved-row placeholders never enter heatmap or daily correct-answer queries.
8. `highest_earned_points` is at least the latest `earned_points` and never exceeds `possible_points`.
9. A quiz set has one to five distinct attempts in stored passage order; its scores are the combined member scores for the latest whole-set completion.
10. Search only compares vectors with the same model ID and dimensions.
11. Chunk offsets reproduce `content` from the passage and adjacent chunks overlap according to the configured policy.
12. A material summary is written only when `summary_markdown` is null. Its Markdown, model ID, and generation time are saved together after the complete summary validates; failed or cancelled generation leaves all three fields null.

These checks stay visible in repository tests instead of being hidden in database triggers.

## Delete Policy

- Deleting an unused material cascades through its passages and search chunks.
- A passage referenced by History cannot be deleted because `attempts.passage_id` uses `RESTRICT`.
- An attempt linked to a quiz set cannot be deleted until the set is removed. Deleting a set cascades only to its membership rows, preserving cached attempts.
- Re-import changed content as a new material instead of mutating passages that support saved evidence.

## Room Implementation Notes

- Use auto-generated `Long` IDs.
- Insert a generated graph in one `@Transaction` method. First completion and every retake update the quiz set and every member attempt in one transaction.
- Index a passage by replacing its `search_chunks` rows in one transaction.
- Encode embeddings as a documented fixed-endian float array in the BLOB and verify byte length against `embedding_dimensions`.
- Keep all exported schemas and test each explicit migration, including version 3 to 4 legacy backfill and version 4 to 5 summary fields.
- Test overlapping set membership, whole-set rollback, daily aggregation, chunk overlap, vector encode/decode, model/dimension filtering, and all five question types with an in-memory Room database where appropriate.
