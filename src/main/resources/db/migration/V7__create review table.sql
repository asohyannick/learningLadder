CREATE TABLE review (
                        id                  UUID                      NOT NULL DEFAULT gen_random_uuid(),

                        course_id           UUID                      NOT NULL,
                        student_id          UUID                      NOT NULL,

                        profile_pic         VARCHAR(1000),

                        rating              INTEGER                   NOT NULL,
                        title               VARCHAR(150),
                        comment             TEXT,

                        status              VARCHAR(20)               NOT NULL DEFAULT 'PENDING',
                        verified_enrollment BOOLEAN                   NOT NULL DEFAULT FALSE,
                        helpful_count       INTEGER                   NOT NULL DEFAULT 0,
                        moderation_note     VARCHAR(500),
                        moderated_by        UUID,
                        moderated_at        TIMESTAMP WITH TIME ZONE,

                        tutor_reply         TEXT,
                        replied_by          UUID,
                        replied_at          TIMESTAMP WITH TIME ZONE,

                        idempotency_key     VARCHAR(100)              NOT NULL,
                        created_at          TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
                        updated_at          TIMESTAMP WITH TIME ZONE,

                        CONSTRAINT pk_review PRIMARY KEY (id),

                        CONSTRAINT fk_review_course
                            FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE CASCADE,
                        CONSTRAINT fk_review_student
                            FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,

                        CONSTRAINT uk_review_course_student UNIQUE (course_id, student_id),
                        CONSTRAINT uk_review_idempotency_key UNIQUE (idempotency_key),

                        CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
                        CONSTRAINT ck_review_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN')),
                        CONSTRAINT ck_review_helpful_count CHECK (helpful_count >= 0)
);

CREATE INDEX idx_review_course        ON review (course_id);
CREATE INDEX idx_review_student       ON review (student_id);
CREATE INDEX idx_review_course_status ON review (course_id, status);
CREATE INDEX idx_review_rating        ON review (rating);