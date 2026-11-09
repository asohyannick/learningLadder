package com.learningLadder.learningLadder.mappers.reviewMapper;

import com.learningLadder.learningLadder.dto.reviewDto.CreateReviewRequestDto;
import com.learningLadder.learningLadder.dto.reviewDto.ReviewResponseDto;
import com.learningLadder.learningLadder.dto.reviewDto.UpdateReviewRequestDto;
import com.learningLadder.learningLadder.entity.review.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ReviewMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "verifiedEnrollment", ignore = true)
    @Mapping(target = "helpfulCount", ignore = true)
    @Mapping(target = "moderationNote", ignore = true)
    @Mapping(target = "moderatedBy", ignore = true)
    @Mapping(target = "moderatedAt", ignore = true)
    @Mapping(target = "tutorReply", ignore = true)
    @Mapping(target = "repliedBy", ignore = true)
    @Mapping(target = "repliedAt", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Review toEntity(CreateReviewRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "verifiedEnrollment", ignore = true)
    @Mapping(target = "helpfulCount", ignore = true)
    @Mapping(target = "moderationNote", ignore = true)
    @Mapping(target = "moderatedBy", ignore = true)
    @Mapping(target = "moderatedAt", ignore = true)
    @Mapping(target = "tutorReply", ignore = true)
    @Mapping(target = "repliedBy", ignore = true)
    @Mapping(target = "repliedAt", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateReviewRequestDto dto, @MappingTarget Review entity);

    @Mapping(source = "course.id", target = "courseId")
    @Mapping(source = "course.name", target = "courseName")
    @Mapping(source = "student.id", target = "studentId")
    @Mapping(source = "student.firstName", target = "studentFirstName")
    @Mapping(source = "student.lastName", target = "studentLastName")
    @Mapping(source = "profilePic", target = "studentProfilePic", ignore = true)
    ReviewResponseDto toResponse(Review entity);
}