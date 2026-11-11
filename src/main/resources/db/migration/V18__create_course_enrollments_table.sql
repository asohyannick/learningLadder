CREATE TABLE course_enrollments (
                                    id             UUID PRIMARY KEY,
                                    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                    course_id      UUID         NOT NULL REFERENCES course (id),
                                    active         BOOLEAN      NOT NULL DEFAULT TRUE,
                                    source         VARCHAR(20)  NOT NULL,
                                    granted_at     TIMESTAMP WITH TIME ZONE NOT NULL,
                                    revoked_at     TIMESTAMP WITH TIME ZONE,
                                    revoke_reason  VARCHAR(255),
                                    version        BIGINT       NOT NULL DEFAULT 0,
                                    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
                                    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,

                                    CONSTRAINT uk_course_enrollment_user_course UNIQUE (user_id, course_id)
);

CREATE INDEX idx_course_enrollment_user_active ON course_enrollments (user_id, active);
CREATE INDEX idx_course_enrollment_course      ON course_enrollments (course_id);