package com.learningLadder.learningLadder.repository.bookingRepository;

import com.learningLadder.learningLadder.entity.booking.Booking;
import com.learningLadder.learningLadder.enums.bookingStatus.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;


public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);



    @Query("""
            select case when count(b) > 0 then true else false end
            from booking b
            where b.tutor.id = :tutorId
              and b.status in :statuses
              and b.id <> :excludeId
              and b.scheduledStart < :end
              and b.scheduledEnd > :start
            """)
    boolean existsTutorOverlap(@Param("tutorId") UUID tutorId,
                               @Param("start") Instant start,
                               @Param("end") Instant end,
                               @Param("statuses") Collection<BookingStatus> statuses,
                               @Param("excludeId") UUID excludeId);

    @Query("""
            select case when count(b) > 0 then true else false end
            from booking b
            where b.student.id = :studentId
              and b.status in :statuses
              and b.id <> :excludeId
              and b.scheduledStart < :end
              and b.scheduledEnd > :start
            """)
    boolean existsStudentOverlap(@Param("studentId") UUID studentId,
                                 @Param("start") Instant start,
                                 @Param("end") Instant end,
                                 @Param("statuses") Collection<BookingStatus> statuses,
                                 @Param("excludeId") UUID excludeId);
}