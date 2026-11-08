CREATE TABLE student_profiles (
                                  id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id                         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                  profile_picture_id              UUID         REFERENCES stored_files (id) ON DELETE SET NULL,

                                  first_name                      VARCHAR(255) NOT NULL,
                                  last_name                       VARCHAR(255) NOT NULL,
                                  email                           VARCHAR(255) NOT NULL,
                                  date_of_birth                   DATE,
                                  gender                          VARCHAR(30),
                                  nationality                     VARCHAR(100),
                                  phone_number                    VARCHAR(30),
                                  description                     TEXT,
                                  idempotency_key     VARCHAR(100)  NOT NULL,

                                  address_line                    VARCHAR(255),
                                  city                            VARCHAR(100),
                                  state_region                    VARCHAR(100),
                                  postal_code                     VARCHAR(20),
                                  country                         VARCHAR(100),

                                  student_number                  VARCHAR(30),
                                  faculty                         VARCHAR(150),
                                  department                      VARCHAR(150),
                                  program_of_study                VARCHAR(150),
                                  current_level                   INTEGER,
                                  enrollment_status               VARCHAR(30)  NOT NULL DEFAULT 'APPLICANT',
                                  admission_date                  DATE,
                                  expected_graduation_date        DATE,
                                  gpa                             NUMERIC(3,2),

                                  emergency_contact_name          VARCHAR(150),
                                  emergency_contact_phone         VARCHAR(30),
                                  emergency_contact_relationship  VARCHAR(50),

                                  linkedin_url                    VARCHAR(255),
                                  website_url                     VARCHAR(255),

                                  created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                  updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                  CONSTRAINT uq_student_profiles_user           UNIQUE (user_id),
                                  CONSTRAINT uq_student_profiles_email          UNIQUE (email),
                                  CONSTRAINT uq_student_profiles_student_number UNIQUE (student_number),
                                  CONSTRAINT chk_student_profiles_gpa           CHECK (gpa IS NULL OR (gpa >= 0 AND gpa <= 5)),
                                  CONSTRAINT chk_student_profiles_level         CHECK (current_level IS NULL OR current_level > 0)
);

CREATE INDEX idx_student_profiles_faculty_department ON student_profiles (faculty, department);
CREATE INDEX idx_student_profiles_enrollment_status  ON student_profiles (enrollment_status);

CREATE TABLE student_profile_hobbies (
                                         student_profile_id UUID         NOT NULL REFERENCES student_profiles (id) ON DELETE CASCADE,
                                         hobby              VARCHAR(100) NOT NULL
);

CREATE INDEX idx_student_profile_hobbies_profile ON student_profile_hobbies (student_profile_id);