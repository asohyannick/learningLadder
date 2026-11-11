CREATE TABLE ai_assist (
                           id                            UUID PRIMARY KEY,
                           name                          VARCHAR(100)  NOT NULL,
                           slug                          VARCHAR(100)  NOT NULL,
                           description                   VARCHAR(500),
                           logo                          VARCHAR(1024),
                           welcome_message               VARCHAR(1000),
                           purpose                       VARCHAR(50),
                           course_id                     UUID REFERENCES course (id),

                           provider                      VARCHAR(30)   NOT NULL DEFAULT 'GROQ',
                           model                         VARCHAR(100)  NOT NULL,
                           system_prompt                 TEXT          NOT NULL,
                           temperature                   NUMERIC(3, 2) NOT NULL DEFAULT 0.70,
                           top_p                         NUMERIC(3, 2) NOT NULL DEFAULT 1.00,
                           max_output_tokens             INTEGER       NOT NULL DEFAULT 1024,
                           context_window_messages       INTEGER       NOT NULL DEFAULT 20,
                           streaming_enabled             BOOLEAN       NOT NULL DEFAULT TRUE,

                           daily_message_limit_per_user  INTEGER       NOT NULL DEFAULT 50,
                           max_input_characters          INTEGER       NOT NULL DEFAULT 4000,
                           moderation_enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
                           active                        BOOLEAN       NOT NULL DEFAULT TRUE,
                           created_by                    UUID,

                           version                       BIGINT        NOT NULL DEFAULT 0,
                           created_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
                           updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL,

                           CONSTRAINT uk_ai_assist_name UNIQUE (name),
                           CONSTRAINT uk_ai_assist_slug UNIQUE (slug),
                           CONSTRAINT ck_ai_assist_provider CHECK (provider IN ('GROQ', 'OPENAI')),
                           CONSTRAINT ck_ai_assist_sampling CHECK (temperature BETWEEN 0 AND 2 AND top_p > 0 AND top_p <= 1),
                           CONSTRAINT ck_ai_assist_limits   CHECK (max_output_tokens > 0 AND context_window_messages >= 0
                               AND daily_message_limit_per_user > 0 AND max_input_characters > 0)
);

CREATE INDEX idx_ai_assist_active ON ai_assist (active);
CREATE INDEX idx_ai_assist_course ON ai_assist (course_id);


CREATE TABLE ai_conversations (
                                  id                       UUID PRIMARY KEY,
                                  assistant_id             UUID         NOT NULL REFERENCES ai_assist (id),
                                  user_id                  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                  title                    VARCHAR(200),
                                  summary                  TEXT,
                                  pinned                   BOOLEAN      NOT NULL DEFAULT FALSE,
                                  archived                 BOOLEAN      NOT NULL DEFAULT FALSE,
                                  message_count            INTEGER      NOT NULL DEFAULT 0,
                                  total_prompt_tokens      BIGINT       NOT NULL DEFAULT 0,
                                  total_completion_tokens  BIGINT       NOT NULL DEFAULT 0,
                                  last_message_at          TIMESTAMP WITH TIME ZONE,
                                  version                  BIGINT       NOT NULL DEFAULT 0,
                                  created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
                                  updated_at               TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_conversation_user_last ON ai_conversations (user_id, last_message_at DESC);
CREATE INDEX idx_ai_conversation_assistant ON ai_conversations (assistant_id);


CREATE TABLE ai_messages (
                             id                    UUID PRIMARY KEY,
                             conversation_id       UUID         NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
                             sequence_number       INTEGER      NOT NULL,
                             role                  VARCHAR(20)  NOT NULL,
                             content               TEXT         NOT NULL,
                             idempotencyKey        TEXT         NOT NULL,
                             status                VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
                             model                 VARCHAR(100),
                             provider_response_id  VARCHAR(100),
                             finish_reason         VARCHAR(30),
                             prompt_tokens         INTEGER,
                             completion_tokens     INTEGER,
                             total_tokens          INTEGER,
                             latency_ms            INTEGER,
                             error_code            VARCHAR(50),
                             error_message         VARCHAR(500),
                             flagged               BOOLEAN      NOT NULL DEFAULT FALSE,
                             feedback_rating       SMALLINT,
                             feedback_comment      VARCHAR(500),
                             created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
                             updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,

                             CONSTRAINT uk_ai_message_sequence UNIQUE (conversation_id, sequence_number),
                             CONSTRAINT ck_ai_message_role     CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL')),
                             CONSTRAINT ck_ai_message_status   CHECK (status IN ('PENDING', 'STREAMING', 'COMPLETED', 'FAILED')),
                             CONSTRAINT ck_ai_message_feedback CHECK (feedback_rating IS NULL OR feedback_rating IN (-1, 1))
);

CREATE INDEX idx_ai_message_status ON ai_messages (status);