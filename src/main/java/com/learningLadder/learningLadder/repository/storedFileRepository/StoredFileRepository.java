package com.learningLadder.learningLadder.repository.storedFileRepository;

import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    @Modifying
    @Query("""
            update StoredFile f
               set f.presignedUrl = :url,
                   f.presignedUrlExpiresAt = :expiresAt,
                   f.updatedAt = :now
             where f.id = :id
            """)
    int updatePresignedUrl(@Param("id") UUID id,
                           @Param("url") String url,
                           @Param("expiresAt") Instant expiresAt,
                           @Param("now") Instant now);
}