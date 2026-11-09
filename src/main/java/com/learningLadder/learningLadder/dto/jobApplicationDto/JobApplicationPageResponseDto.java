package com.learningLadder.learningLadder.dto.jobApplicationDto;

import java.io.Serializable;
import java.util.List;

public record JobApplicationPageResponseDto(
        List<JobApplicationResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public JobApplicationPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}