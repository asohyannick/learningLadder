CREATE TABLE time_table (
                            id               UUID            PRIMARY KEY,

                            course_id        UUID            NOT NULL,

                            title            VARCHAR(200)    NOT NULL,
                            description      TEXT,
                            notes            TEXT,
                            idempotency_key  VARCHAR(100)    NOT NULL,

                            start_date       DATE            NOT NULL,
                            end_date         DATE,
                            start_time       TIME            NOT NULL,
                            end_time         TIME            NOT NULL,
                            timezone         VARCHAR(60)     NOT NULL DEFAULT 'UTC',
                            recurring        BOOLEAN         NOT NULL DEFAULT FALSE,
                            day_of_week      VARCHAR(15),

                            session_mode     VARCHAR(20)     NOT NULL DEFAULT 'ONLINE',
                            location         VARCHAR(255),
                            meeting_url      VARCHAR(500),
                            capacity         INTEGER,
                            status           VARCHAR(30)     NOT NULL DEFAULT 'SCHEDULED',

                            created_by       UUID,
                            created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                            updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

                            CONSTRAINT uq_time_table_idempotency_key UNIQUE (idempotency_key),

                            CONSTRAINT fk_time_table_course
                                FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE CASCADE,

                            CONSTRAINT chk_time_table_session_mode
                                CHECK (session_mode IN ('ONLINE', 'IN_PERSON', 'HYBRID')),
                            CONSTRAINT chk_time_table_status
                                CHECK (status IN ('SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED', 'POSTPONED')),
                            CONSTRAINT chk_time_table_day_of_week
                                CHECK (day_of_week IS NULL OR day_of_week IN
                                                              ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),

                            CONSTRAINT chk_time_table_times
                                CHECK (end_time > start_time),
                            CONSTRAINT chk_time_table_dates
                                CHECK (end_date IS NULL OR end_date >= start_date),
                            CONSTRAINT chk_time_table_capacity
                                CHECK (capacity IS NULL OR capacity > 0),
                            CONSTRAINT chk_time_table_recurrence
                                CHECK (
                                    (recurring AND day_of_week IS NOT NULL AND end_date IS NOT NULL)
                                        OR
                                    (NOT recurring AND day_of_week IS NULL AND end_date IS NULL)
                                    )
);

CREATE INDEX idx_time_table_course_start   ON time_table (course_id, start_date);
CREATE INDEX idx_time_table_status         ON time_table (status);
CREATE INDEX idx_time_table_start_date     ON time_table (start_date);
CREATE INDEX idx_time_table_day_of_week    ON time_table (day_of_week);
CREATE INDEX idx_time_table_created_by     ON time_table (created_by);
CREATE INDEX idx_time_table_created_at     ON time_table (created_at DESC);