package com.example.media.service;

import com.example.media.dto.MediaFileResponse;
import com.example.media.ports.StoragePort;
import com.example.media.validation.MediaContentValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@Service
public final class MediaFileStorageService {

    private final String uploadDir;
    private final StoragePort storage;

    public MediaFileStorageService(@Value("${media.upload-dir}") String uploadDir,
                                   StoragePort storage) {
        this.storage = storage;
        this.uploadDir = uploadDir;
    }

    public MediaFileResponse store(MultipartFile file) throws IOException {
        String extension = extensionOf(file.getOriginalFilename());
        if (!MediaContentValidator.ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type: " + extension);
        }
        String fileName = UUID.randomUUID() + "." + extension;
        String fullPath = String.join("/", uploadDir, fileName);

        String url = storage.uploadFile(file.getBytes(), fullPath, file.getContentType());

        return new MediaFileResponse(url, fileName, uploadDir);
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File has no extension");
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
    }
}
