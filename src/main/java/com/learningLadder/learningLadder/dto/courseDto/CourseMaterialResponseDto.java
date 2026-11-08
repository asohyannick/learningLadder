package com.learningLadder.learningLadder.dto.courseDto;

import java.io.Serializable;
import java.util.UUID;

public record CourseMaterialResponseDto(
        UUID id,
        String title,
        String description,
        int sortOrder,
        boolean freePreview,
        UUID fileId,
        String fileName,
        String contentType,
        long sizeBytes,
        String url
) implements Serializable { }