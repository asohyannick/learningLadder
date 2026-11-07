package com.learningLadder.learningLadder.entity.storedFile;

import com.learningLadder.learningLadder.enums.fileCategory.FileCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FileCategory category;

    @Column(name = "presigned_url", nullable = false, columnDefinition = "TEXT")
    private String presignedUrl;

    @Column(name = "presigned_url_expires_at", nullable = false)
    private Instant presignedUrlExpiresAt;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}