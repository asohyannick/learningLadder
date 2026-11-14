package com.learningLadder.learningLadder.utils.fileUrlResolver;

import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileUrlResolver {

    private final MinioFileStorageService storage;

    @Named("fileUrl")
    public String fileUrl(StoredFile file) {
        return file == null ? null : storage.resolveUrl(file);
    }
}