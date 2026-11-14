package com.learningLadder.learningLadder.dto.courseDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.courseLevel.CourseLevel;
import com.learningLadder.learningLadder.enums.courseStatus.CourseStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CourseSummaryResponseDto(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        String category,
        CourseLevel level,
        String language,
        Integer durationMinutes,
        BigDecimal price,
        BigDecimal discount,
        String currency,
        CourseStatus status,
        boolean published,
        String thumbnailUrl,
        String promoVideoUrl,
        Instant createdAt
) implements Serializable { }