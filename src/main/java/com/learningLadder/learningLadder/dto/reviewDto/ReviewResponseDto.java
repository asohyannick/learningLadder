package com.learningLadder.learningLadder.dto.reviewDto;

import com.learningLadder.learningLadder.enums.reviewStatus.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A course review together with the student who wrote it")
public record ReviewResponseDto(

        UUID id,

        UUID courseId,
        String courseName,

        UUID studentId,
        String studentFirstName,
        String studentLastName,
        @Schema(description = "Profile picture of the reviewer")
        String studentProfilePic,

        int rating,
        String title,
        String comment,

        ReviewStatus status,
        boolean verifiedEnrollment,
        int helpfulCount,

        String tutorReply,
        UUID repliedBy,
        Instant repliedAt,

        Instant createdAt,
        Instant updatedAt
) {
        public ReviewResponseDto withStudentProfilePic(String url) {
                return new ReviewResponseDto(id, courseId, courseName, studentId, studentFirstName, studentLastName,
                        url, rating, title, comment, status, verifiedEnrollment, helpfulCount,
                        tutorReply, repliedBy, repliedAt, createdAt, updatedAt);
        }
}