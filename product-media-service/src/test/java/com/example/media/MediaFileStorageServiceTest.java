package com.example.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaFileStorageServiceTest {

    @TempDir
    Path uploadDir;

    MediaFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new MediaFileStorageService(uploadDir.toString());
    }

    @Test
    void store_allowedExtension_savesFileAndReturnsGeneratedFilename() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-bytes".getBytes());

        String storedFilename = storageService.store(file);

        assertThat(storedFilename).endsWith(".jpg");
        assertThat(Files.exists(uploadDir.resolve(storedFilename))).isTrue();
    }

    @Test
    void store_disallowedExtension_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> storageService.store(file)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void store_noExtension_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "noextension", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> storageService.store(file)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resolve_pathTraversalAttempt_throwsBadRequest() {
        assertThatThrownBy(() -> storageService.resolve("../../../etc/passwd"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resolve_plainFilename_resolvesWithinUploadDir() {
        Path resolved = storageService.resolve("abc.jpg");

        assertThat(resolved).isEqualTo(uploadDir.resolve("abc.jpg"));
    }
}
