CREATE TABLE stored_files (
                              id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                              object_key                VARCHAR(512) NOT NULL,      -- permanent location of the file in the MinIO bucket
                              original_file_name        VARCHAR(255),
                              content_type              VARCHAR(255),
                              size_bytes                BIGINT       NOT NULL DEFAULT 0,

                              category                  VARCHAR(255) NOT NULL,      -- IMAGE, DOCUMENT, VIDEO

                              presigned_url             TEXT         NOT NULL,      -- presigned URLs are 700+ characters
                              presigned_url_expires_at  TIMESTAMPTZ  NOT NULL,

                              uploaded_by               UUID,

                              created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
                              updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),

                              CONSTRAINT uq_stored_files_object_key UNIQUE (object_key),
                              CONSTRAINT fk_stored_files_uploaded_by
                                  FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_stored_files_uploaded_by ON stored_files (uploaded_by);

CREATE INDEX idx_stored_files_presigned_url_expires_at ON stored_files (presigned_url_expires_at);