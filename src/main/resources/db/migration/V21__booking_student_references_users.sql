ALTER TABLE booking DROP CONSTRAINT IF EXISTS booking_student_id_fkey;
ALTER TABLE booking DROP CONSTRAINT IF EXISTS fk_booking_student_user;

UPDATE booking b
SET student_id = sp.user_id
    FROM student_profiles sp
WHERE b.student_id = sp.id;

ALTER TABLE booking
    ADD CONSTRAINT fk_booking_student_user FOREIGN KEY (student_id) REFERENCES users (id);