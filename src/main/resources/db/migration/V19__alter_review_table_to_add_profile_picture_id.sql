ALTER TABLE review ADD COLUMN profile_picture_id UUID REFERENCES stored_files (id);
ALTER TABLE review DROP COLUMN IF EXISTS profilepic;