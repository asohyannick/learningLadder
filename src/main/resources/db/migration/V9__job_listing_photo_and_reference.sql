ALTER TABLE job_listings
    ADD COLUMN job_photo_file_id UUID REFERENCES stored_files (id) ON DELETE SET NULL;

CREATE SEQUENCE job_reference_seq START 1;