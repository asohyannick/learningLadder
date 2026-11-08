CREATE TABLE course (
                        id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        name                VARCHAR(255)  NOT NULL,
                        slug                VARCHAR(255)  NOT NULL,
                        idempotency_key     VARCHAR(100)  NOT NULL,
                        short_description   VARCHAR(500),
                        description         TEXT,
                        learning_outcomes   TEXT,
                        requirements        TEXT,
                        category            VARCHAR(100),
                        course_level        VARCHAR(255),
                        language            VARCHAR(50),
                        duration_minutes    INTEGER,
                        price               NUMERIC(12,2),
                        discount            NUMERIC(12,2),
                        currency            VARCHAR(3)    NOT NULL,
                        status              VARCHAR(255),
                        published           BOOLEAN       NOT NULL DEFAULT FALSE,
                        published_at        TIMESTAMPTZ,
                        thumbnail_id   UUID REFERENCES stored_files (id) ON DELETE SET NULL,
                        promo_video_id UUID REFERENCES stored_files (id) ON DELETE SET NULL,
                        syllabus_id    UUID REFERENCES stored_files (id) ON DELETE SET NULL,
                        created_by          UUID,
                        created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                        updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                        CONSTRAINT uq_course_slug            UNIQUE (slug),
                        CONSTRAINT uq_course_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_course_category  ON course (category);
CREATE INDEX idx_course_published ON course (published);

CREATE TABLE course_materials (
                                  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  course_id    UUID          NOT NULL REFERENCES course (id) ON DELETE CASCADE,
                                  file_id      UUID          NOT NULL REFERENCES stored_files (id),
                                  title        VARCHAR(255)  NOT NULL,
                                  description  VARCHAR(1000),
                                  sort_order   INTEGER       NOT NULL DEFAULT 0,
                                  free_preview BOOLEAN       NOT NULL DEFAULT FALSE,
                                  created_at   TIMESTAMPTZ,
                                  updated_at   TIMESTAMPTZ
);

CREATE INDEX idx_course_materials_course ON course_materials (course_id);