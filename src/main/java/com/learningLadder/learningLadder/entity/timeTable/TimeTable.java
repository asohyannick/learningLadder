package com.learningLadder.learningLadder.entity.timeTable;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import com.learningLadder.learningLadder.enums.timeTableStatus.TimeTableStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity(
        name = "time_table"
)
@Table(
        name = "time_table"
)
@Getter
@Setter
@NoArgsConstructor
public class TimeTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone = "UTC";

    @Column(name = "recurring", nullable = false)
    private boolean recurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 15)
    private DayOfWeek dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_mode", nullable = false, length = 20)
    private SessionMode sessionMode = SessionMode.ONLINE;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TimeTableStatus status = TimeTableStatus.SCHEDULED;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}