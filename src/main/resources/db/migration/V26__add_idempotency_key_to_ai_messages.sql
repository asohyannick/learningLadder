ALTER TABLE ai_messages
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_messages_idempotency_key
    ON ai_messages (idempotency_key);