CREATE TABLE tutor_profiles (
                                id                              UUID            PRIMARY KEY,

                                user_id                         UUID            NOT NULL,
                                profile_picture_id              UUID,

                                first_name                      VARCHAR(255)    NOT NULL,
                                last_name                       VARCHAR(255)    NOT NULL,
                                email                           VARCHAR(255)    NOT NULL,
                                date_of_birth                   DATE,
                                gender                          VARCHAR(30),
                                nationality                     VARCHAR(100),
                                phone_number                    VARCHAR(30),
                                description                     TEXT,

                                address_line                    VARCHAR(255),
                                city                            VARCHAR(100),
                                state_region                    VARCHAR(100),
                                postal_code                     VARCHAR(20),
                                country                         VARCHAR(100),

                                idempotency_key                 VARCHAR(100)    NOT NULL,

                                tutor_number                    VARCHAR(30),
                                title                           VARCHAR(30),
                                faculty                         VARCHAR(150),
                                department                      VARCHAR(150),
                                highest_qualification           VARCHAR(150),
                                field_of_specialization         VARCHAR(150),
                                years_of_experience             INTEGER,
                                hourly_rate                     NUMERIC(10, 2),
                                currency                        VARCHAR(3),

                                status                          VARCHAR(30)     NOT NULL DEFAULT 'PENDING_APPROVAL',
                                joined_date                     DATE,

                                emergency_contact_name          VARCHAR(150),
                                emergency_contact_phone         VARCHAR(30),
                                emergency_contact_relationship  VARCHAR(50),

                                linkedin_url                    VARCHAR(255),
                                website_url                     VARCHAR(255),

                                created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

                                CONSTRAINT uq_tutor_profiles_user             UNIQUE (user_id),
                                CONSTRAINT uq_tutor_profiles_email            UNIQUE (email),
                                CONSTRAINT uq_tutor_profiles_idempotency_key  UNIQUE (idempotency_key),
                                CONSTRAINT uq_tutor_profiles_tutor_number     UNIQUE (tutor_number),

                                CONSTRAINT fk_tutor_profiles_user
                                    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                                CONSTRAINT fk_tutor_profiles_profile_picture
                                    FOREIGN KEY (profile_picture_id) REFERENCES stored_files (id) ON DELETE SET NULL,

                                CONSTRAINT chk_tutor_profiles_years_of_experience
                                    CHECK (years_of_experience IS NULL OR years_of_experience >= 0),
                                CONSTRAINT chk_tutor_profiles_hourly_rate
                                    CHECK (hourly_rate IS NULL OR hourly_rate >= 0)
);

CREATE INDEX idx_tutor_profiles_profile_picture   ON tutor_profiles (profile_picture_id);
CREATE INDEX idx_tutor_profiles_status            ON tutor_profiles (status);
CREATE INDEX idx_tutor_profiles_faculty           ON tutor_profiles (faculty);
CREATE INDEX idx_tutor_profiles_department        ON tutor_profiles (department);
CREATE INDEX idx_tutor_profiles_specialization    ON tutor_profiles (field_of_specialization);
CREATE INDEX idx_tutor_profiles_years_experience  ON tutor_profiles (years_of_experience);
CREATE INDEX idx_tutor_profiles_joined_date       ON tutor_profiles (joined_date);
CREATE INDEX idx_tutor_profiles_created_at        ON tutor_profiles (created_at DESC);


CREATE TABLE tutor_profile_hobbies (
                                       tutor_profile_id    UUID            NOT NULL,
                                       hobby               VARCHAR(100)    NOT NULL,
                                       CONSTRAINT fk_tutor_profile_hobbies_profile
                                           FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id) ON DELETE CASCADE
);
CREATE INDEX idx_tutor_profile_hobbies_profile ON tutor_profile_hobbies (tutor_profile_id);

CREATE TABLE tutor_profile_languages (
                                         tutor_profile_id    UUID            NOT NULL,
                                         language            VARCHAR(100)    NOT NULL,
                                         CONSTRAINT fk_tutor_profile_languages_profile
                                             FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id) ON DELETE CASCADE
);
CREATE INDEX idx_tutor_profile_languages_profile ON tutor_profile_languages (tutor_profile_id);

CREATE TABLE tutor_profile_subjects (
                                        tutor_profile_id    UUID            NOT NULL,
                                        subject             VARCHAR(150)    NOT NULL,
                                        CONSTRAINT fk_tutor_profile_subjects_profile
                                            FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id) ON DELETE CASCADE
);
CREATE INDEX idx_tutor_profile_subjects_profile ON tutor_profile_subjects (tutor_profile_id);
-- Supports the "subject taught" search filter
CREATE INDEX idx_tutor_profile_subjects_subject ON tutor_profile_subjects (LOWER(subject));

CREATE TABLE tutor_profile_certifications (
                                              tutor_profile_id    UUID            NOT NULL,
                                              certification       VARCHAR(255)    NOT NULL,
                                              CONSTRAINT fk_tutor_profile_certifications_profile
                                                  FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id) ON DELETE CASCADE
);
CREATE INDEX idx_tutor_profile_certifications_profile ON tutor_profile_certifications (tutor_profile_id);