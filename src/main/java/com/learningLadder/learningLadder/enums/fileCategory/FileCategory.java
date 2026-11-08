package com.learningLadder.learningLadder.enums.fileCategory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum FileCategory {

    IMAGE("images", 10L * 1024 * 1024,
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif")),

    DOCUMENT("documents", 25L * 1024 * 1024,
            Set.of("application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain")),

    VIDEO("videos", 500L * 1024 * 1024,
            Set.of("video/mp4", "video/webm", "video/quicktime"));

    private final String folder;
    private final long maxBytes;
    private final Set<String> allowedContentTypes;

    public boolean allows(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase());
    }

    public static FileCategory fromContentType(String contentType) {
        if (contentType != null) {
            String type = contentType.toLowerCase();
            if (type.startsWith("image/")) return IMAGE;
            if (type.startsWith("video/")) return VIDEO;
        }
        return DOCUMENT;
    }
}