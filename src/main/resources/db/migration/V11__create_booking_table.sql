CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE booking (
                         id                     UUID PRIMARY KEY,
                         booking_reference      VARCHAR(30)  NOT NULL,

                         student_id             UUID         NOT NULL REFERENCES student_profiles (id),
                         tutor_id               UUID         NOT NULL REFERENCES tutor_profiles (id),
                         course_id              UUID                  REFERENCES course (id),

                         topic                  VARCHAR(200) NOT NULL,
                         student_notes          TEXT,

                         scheduled_start        TIMESTAMP WITH TIME ZONE NOT NULL,
                         scheduled_end          TIMESTAMP WITH TIME ZONE NOT NULL,
                         duration_minutes       INTEGER      NOT NULL,
                         time_zone              VARCHAR(50)  NOT NULL DEFAULT 'UTC',

                         session_mode           VARCHAR(30)  NOT NULL,
                         meeting_platform       VARCHAR(50),
                         meeting_link           VARCHAR(500),
                         meeting_id             VARCHAR(100),
                         meeting_passcode       VARCHAR(100),
                         location_name          VARCHAR(200),
                         address_line           VARCHAR(255),
                         city                   VARCHAR(100),
                         state_region           VARCHAR(100),
                         country                VARCHAR(100),
                         latitude               NUMERIC(9, 6),
                         longitude              NUMERIC(9, 6),
                         location_instructions  VARCHAR(500),

                         price                  NUMERIC(12, 2),
                         currency               VARCHAR(3)   DEFAULT 'USD',
                         payment_status         VARCHAR(30),

                         status                 VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
                         confirmed_at           TIMESTAMP WITH TIME ZONE,
                         rejected_at            TIMESTAMP WITH TIME ZONE,
                         rejection_reason       VARCHAR(500),
                         cancelled_at           TIMESTAMP WITH TIME ZONE,
                         cancelled_by           UUID,
                         cancellation_reason    VARCHAR(500),
                         completed_at           TIMESTAMP WITH TIME ZONE,
                         reschedule_count       INTEGER      NOT NULL DEFAULT 0,
                         last_rescheduled_at    TIMESTAMP WITH TIME ZONE,
                         tutor_notes            VARCHAR(2000),

                         reminder_sent          BOOLEAN      NOT NULL DEFAULT FALSE,
                         reminder_sent_at       TIMESTAMP WITH TIME ZONE,

                         idempotency_key        VARCHAR(100) NOT NULL,
                         version                BIGINT       NOT NULL DEFAULT 0,
                         created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
                         updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,

                         CONSTRAINT uk_booking_reference       UNIQUE (booking_reference),
                         CONSTRAINT uk_booking_idempotency_key UNIQUE (idempotency_key),
                         CONSTRAINT ck_booking_time_range      CHECK (scheduled_end > scheduled_start),
                         CONSTRAINT ck_booking_price           CHECK (price IS NULL OR price >= 0),
                         CONSTRAINT ck_booking_status          CHECK (status IN
                                                                      ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),

                         CONSTRAINT ex_booking_tutor_no_overlap EXCLUDE USING gist (
        tutor_id WITH =,
        tstzrange(scheduled_start, scheduled_end, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED'))
);

CREATE INDEX idx_booking_tutor_start   ON booking (tutor_id, scheduled_start);
CREATE INDEX idx_booking_student_start ON booking (student_id, scheduled_start);
CREATE INDEX idx_booking_course        ON booking (course_id);
CREATE INDEX idx_booking_status        ON booking (status);
CREATE INDEX idx_booking_status_start  ON booking (status, scheduled_start);