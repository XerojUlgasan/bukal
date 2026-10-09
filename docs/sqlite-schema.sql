PRAGMA foreign_keys = ON;

-- Reference schema for Room database version 5.
-- Model files and retained source documents stay in app-specific storage.
-- Small preferences, including selected model IDs, stay in DataStore.

CREATE TABLE materials (
    id INTEGER PRIMARY KEY,
    display_name TEXT NOT NULL CHECK (length(trim(display_name)) > 0),
    document_format TEXT NOT NULL
        CHECK (document_format IN ('txt', 'pdf', 'docx', 'pptx')),
    mime_type TEXT,
    retained_file_path TEXT
        CHECK (retained_file_path IS NULL OR length(trim(retained_file_path)) > 0),
    imported_at_epoch_ms INTEGER NOT NULL CHECK (imported_at_epoch_ms >= 0),
    summary_markdown TEXT,
    summary_model_id TEXT,
    summarized_at_epoch_ms INTEGER,
    CHECK (
        (
            summary_markdown IS NULL
            AND summary_model_id IS NULL
            AND summarized_at_epoch_ms IS NULL
        )
        OR
        (
            length(trim(summary_markdown)) > 0
            AND length(trim(summary_model_id)) > 0
            AND summarized_at_epoch_ms >= 0
        )
    )
);

CREATE INDEX idx_materials_imported_at
    ON materials(imported_at_epoch_ms DESC);

CREATE TABLE passages (
    id INTEGER PRIMARY KEY,
    material_id INTEGER NOT NULL,
    source_id TEXT NOT NULL CHECK (length(trim(source_id)) > 0),
    position INTEGER NOT NULL CHECK (position >= 0),
    title TEXT CHECK (title IS NULL OR length(trim(title)) > 0),
    content TEXT NOT NULL CHECK (length(trim(content)) > 0),
    FOREIGN KEY (material_id) REFERENCES materials(id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    UNIQUE (material_id, source_id),
    UNIQUE (material_id, position)
);

CREATE TABLE search_chunks (
    id INTEGER PRIMARY KEY,
    passage_id INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    start_offset INTEGER NOT NULL CHECK (start_offset >= 0),
    end_offset INTEGER NOT NULL CHECK (end_offset > start_offset),
    content TEXT NOT NULL CHECK (length(trim(content)) > 0),
    embedding_model_id TEXT,
    embedding_dimensions INTEGER,
    embedding_vector BLOB,
    FOREIGN KEY (passage_id) REFERENCES passages(id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    UNIQUE (passage_id, chunk_index),
    CHECK (
        (
            embedding_model_id IS NULL
            AND embedding_dimensions IS NULL
            AND embedding_vector IS NULL
        )
        OR
        (
            embedding_model_id IS NOT NULL
            AND embedding_dimensions IS NOT NULL
            AND embedding_vector IS NOT NULL
            AND length(trim(embedding_model_id)) > 0
            AND embedding_dimensions > 0
            AND length(embedding_vector) > 0
        )
    )
);

CREATE INDEX idx_search_chunks_embedding_model
    ON search_chunks(embedding_model_id);

CREATE TABLE attempts (
    id INTEGER PRIMARY KEY,
    passage_id INTEGER NOT NULL,
    quiz_model_id TEXT NOT NULL CHECK (length(trim(quiz_model_id)) > 0),
    generated_at_epoch_ms INTEGER NOT NULL CHECK (generated_at_epoch_ms >= 0),
    status TEXT NOT NULL CHECK (status IN ('saved', 'completed')),
    completed_at_epoch_ms INTEGER NOT NULL CHECK (completed_at_epoch_ms >= 0),
    completed_local_date TEXT NOT NULL
        CHECK (
            length(completed_local_date) = 10
            AND completed_local_date GLOB
                '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        ),
    earned_points REAL NOT NULL
        CHECK (earned_points >= 0 AND earned_points <= 5),
    highest_earned_points REAL NOT NULL DEFAULT 0
        CHECK (highest_earned_points >= earned_points AND highest_earned_points <= 5),
    possible_points REAL NOT NULL
        CHECK (possible_points >= 0 AND possible_points <= 5),
    CHECK (earned_points <= possible_points),
    CHECK (highest_earned_points <= possible_points),
    FOREIGN KEY (passage_id) REFERENCES passages(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX idx_attempts_completed_at
    ON attempts(completed_at_epoch_ms DESC);

CREATE INDEX idx_attempts_generated_at
    ON attempts(generated_at_epoch_ms DESC);

CREATE INDEX idx_attempts_completed_local_date
    ON attempts(completed_local_date);

CREATE INDEX idx_attempts_passage_id
    ON attempts(passage_id);

CREATE TABLE quiz_sets (
    id INTEGER PRIMARY KEY,
    selection_key TEXT NOT NULL UNIQUE,
    created_at_epoch_ms INTEGER NOT NULL CHECK (created_at_epoch_ms >= 0),
    status TEXT NOT NULL DEFAULT 'saved' CHECK (status IN ('saved', 'completed')),
    completed_at_epoch_ms INTEGER NOT NULL CHECK (completed_at_epoch_ms >= 0),
    completed_local_date TEXT NOT NULL
        CHECK (
            length(completed_local_date) = 10
            AND completed_local_date GLOB
                '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        ),
    earned_points REAL NOT NULL CHECK (earned_points >= 0),
    highest_earned_points REAL NOT NULL DEFAULT 0
        CHECK (highest_earned_points >= earned_points),
    possible_points REAL NOT NULL CHECK (possible_points >= 0),
    CHECK (earned_points <= possible_points),
    CHECK (highest_earned_points <= possible_points)
);

CREATE UNIQUE INDEX index_quiz_sets_selection_key
    ON quiz_sets(selection_key);

CREATE INDEX idx_quiz_sets_created_at
    ON quiz_sets(created_at_epoch_ms DESC);

CREATE INDEX idx_quiz_sets_completed_at
    ON quiz_sets(completed_at_epoch_ms DESC);

CREATE INDEX idx_quiz_sets_completed_local_date
    ON quiz_sets(completed_local_date);

CREATE TABLE quiz_set_items (
    quiz_set_id INTEGER NOT NULL,
    attempt_id INTEGER NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (quiz_set_id, attempt_id),
    FOREIGN KEY (quiz_set_id) REFERENCES quiz_sets(id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    FOREIGN KEY (attempt_id) REFERENCES attempts(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE (quiz_set_id, position)
);

CREATE UNIQUE INDEX index_quiz_set_items_quiz_set_id_position
    ON quiz_set_items(quiz_set_id, position);

CREATE INDEX index_quiz_set_items_attempt_id
    ON quiz_set_items(attempt_id);

CREATE TABLE questions (
    id INTEGER PRIMARY KEY,
    attempt_id INTEGER NOT NULL,
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 4),
    quiz_type TEXT NOT NULL
        CHECK (
            quiz_type IN (
                'multiple_choice',
                'fill_in_the_blank',
                'identification',
                'true_false',
                'matching',
                'explanation'
            )
        ),
    prompt TEXT NOT NULL CHECK (length(trim(prompt)) > 0),
    explanation TEXT NOT NULL CHECK (length(trim(explanation)) > 0),
    source_id TEXT NOT NULL CHECK (length(trim(source_id)) > 0),
    evidence TEXT NOT NULL CHECK (length(trim(evidence)) > 0),
    option_0 TEXT,
    option_1 TEXT,
    option_2 TEXT,
    option_3 TEXT,
    correct_option_index INTEGER,
    reference_answer TEXT,
    grading_criteria TEXT,
    selected_option_index INTEGER
        CHECK (selected_option_index IS NULL OR selected_option_index BETWEEN 0 AND 3),
    text_response TEXT,
    result TEXT NOT NULL
        CHECK (
            result IN (
                'correct',
                'partially_correct',
                'incorrect',
                'uncertain',
                'evaluation_failed',
                'unanswered'
            )
        ),
    ai_feedback TEXT,
    evaluation_evidence TEXT,
    earned_points REAL NOT NULL CHECK (earned_points IN (0, 0.5, 1)),
    possible_points REAL NOT NULL CHECK (possible_points IN (0, 1)),
    CHECK (earned_points <= possible_points),
    FOREIGN KEY (attempt_id) REFERENCES attempts(id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    UNIQUE (attempt_id, position),
    CHECK (
        (
            quiz_type = 'multiple_choice'
            AND option_0 IS NOT NULL
            AND option_1 IS NOT NULL
            AND option_2 IS NOT NULL
            AND option_3 IS NOT NULL
            AND correct_option_index IS NOT NULL
            AND length(trim(option_0)) > 0
            AND length(trim(option_1)) > 0
            AND length(trim(option_2)) > 0
            AND length(trim(option_3)) > 0
            AND correct_option_index BETWEEN 0 AND 3
            AND reference_answer IS NULL
            AND grading_criteria IS NULL
        )
        OR
        (
            quiz_type = 'true_false'
            AND option_0 = 'True'
            AND option_1 = 'False'
            AND option_2 IS NULL
            AND option_3 IS NULL
            AND correct_option_index BETWEEN 0 AND 1
            AND reference_answer IS NULL
            AND grading_criteria IS NULL
        )
        OR
        (
            quiz_type IN ('fill_in_the_blank', 'identification', 'explanation')
            AND option_0 IS NULL
            AND option_1 IS NULL
            AND option_2 IS NULL
            AND option_3 IS NULL
            AND correct_option_index IS NULL
            AND reference_answer IS NOT NULL
            AND length(trim(reference_answer)) > 0
            AND grading_criteria IS NULL
        )
        OR
        (
            quiz_type = 'matching'
            AND option_0 IS NULL
            AND option_1 IS NULL
            AND option_2 IS NULL
            AND option_3 IS NULL
            AND correct_option_index IS NULL
            AND reference_answer IS NULL
            AND grading_criteria IS NULL
        )
    )
);

CREATE TABLE matching_pairs (
    question_id INTEGER NOT NULL,
    left_id TEXT NOT NULL CHECK (length(trim(left_id)) > 0),
    left_text TEXT NOT NULL CHECK (length(trim(left_text)) > 0),
    left_position INTEGER NOT NULL CHECK (left_position >= 0),
    right_id TEXT NOT NULL CHECK (length(trim(right_id)) > 0),
    right_text TEXT NOT NULL CHECK (length(trim(right_text)) > 0),
    right_position INTEGER NOT NULL CHECK (right_position >= 0),
    selected_right_id TEXT,
    PRIMARY KEY (question_id, left_id),
    FOREIGN KEY (question_id) REFERENCES questions(id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    FOREIGN KEY (question_id, selected_right_id)
        REFERENCES matching_pairs(question_id, right_id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    UNIQUE (question_id, right_id),
    UNIQUE (question_id, left_position),
    UNIQUE (question_id, right_position)
);

CREATE INDEX index_matching_pairs_question_id_selected_right_id
    ON matching_pairs(question_id, selected_right_id);
