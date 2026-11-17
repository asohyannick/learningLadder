package com.learningLadder.learningLadder.utils.libraryLinkResolver;

import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.libraryLoanDto.LibraryBookPageResponseDto;
import com.learningLadder.learningLadder.dto.libraryLoanDto.LibraryBookResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Turns book cover storage keys into temporary download links.
 * Runs after the cache, so every response carries a link that is still valid.
 */
@Component
@RequiredArgsConstructor
public class LibraryLinkResolver {

    /** Covers are not personal data, so a longer link is fine; each response still gets a fresh one. */
    private static final Duration COVER_LINK_VALIDITY = Duration.ofHours(24);

    private final MinioFileStorageService storage;

    public LibraryBookResponseDto withLinks(LibraryBookResponseDto book) {
        if (book == null || book.coverImage() == null || book.coverImage().isBlank()) {
            return book;
        }
        if (book.coverImage().startsWith("http")) {
            return book;
        }
        return book.withCoverImage(storage.presignedUrl(book.coverImage(), COVER_LINK_VALIDITY));
    }

    public LibraryBookPageResponseDto withLinks(LibraryBookPageResponseDto page) {
        List<LibraryBookResponseDto> content = page.content().stream()
                .map(this::withLinks)
                .toList();

        return new LibraryBookPageResponseDto(content, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.first(), page.last());
    }
}