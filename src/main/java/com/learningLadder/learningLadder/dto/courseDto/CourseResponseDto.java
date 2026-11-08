package com.learningLadder.learningLadder.dto.courseDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.courseLevel.CourseLevel;
import com.learningLadder.learningLadder.enums.courseStatus.CourseStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CourseResponseDto(
        UUID id,
        String name,
        String slug,
        String idempotencyKey,
        String shortDescription,
        String description,
        String learningOutcomes,
        String requirements,
        String category,
        CourseLevel level,
        String language,
        Integer durationMinutes,
        BigDecimal price,
        BigDecimal discount,
        String currency,
        CourseStatus status,
        boolean published,
        Instant publishedAt,
        String thumbnailUrl,
        String promoVideoUrl,
        String syllabusUrl,
        List<CourseMaterialResponseDto> materials,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) implements Serializable { }