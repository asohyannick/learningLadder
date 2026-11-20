DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'job_listings' AND column_name = 'jobphotourl') THEN

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'job_listings' AND column_name = 'job_photo_url') THEN
ALTER TABLE job_listings RENAME COLUMN jobphotourl TO job_photo_url;
ELSE

UPDATE job_listings SET job_photo_url = COALESCE(job_photo_url, jobphotourl);
ALTER TABLE job_listings DROP COLUMN jobphotourl;
END IF;
END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'job_listings' AND column_name = 'job_photo_url') THEN
ALTER TABLE job_listings ALTER COLUMN job_photo_url DROP NOT NULL;
END IF;
END $$;