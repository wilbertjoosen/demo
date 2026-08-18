package com.example.media;

import com.example.media.dto.MediaFileResponse;
import com.example.media.ports.StoragePort;
import com.example.media.service.MediaFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaFileStorageServiceTest {

    private static final String UPLOAD_DIR = "media";

    @Mock
    StoragePort storage;

    MediaFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new MediaFileStorageService(UPLOAD_DIR, storage);
    }

    @Test
    void store_allowedExtension_uploadsToStorageAndReturnsResponse() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-bytes".getBytes());
        when(storage.uploadFile(any(byte[].class), anyString(), anyString()))
                .thenReturn("https://bucket.s3.eu-west-1.amazonaws.com/media/generated.jpg");

        MediaFileResponse response = storageService.store(file);

        assertThat(response.url()).isEqualTo("https://bucket.s3.eu-west-1.amazonaws.com/media/generated.jpg");
        assertThat(response.fileName()).endsWith(".jpg");
        assertThat(response.path()).isEqualTo(UPLOAD_DIR);
    }

    @Test
    void store_allowedExtension_passesGeneratedKeyBytesAndContentTypeToStorage() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-bytes".getBytes());
        when(storage.uploadFile(any(byte[].class), anyString(), anyString())).thenReturn("https://example.com/x.jpg");

        MediaFileResponse response = storageService.store(file);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage).uploadFile(bytesCaptor.capture(), keyCaptor.capture(), contentTypeCaptor.capture());

        assertThat(bytesCaptor.getValue()).isEqualTo("fake-bytes".getBytes());
        assertThat(keyCaptor.getValue()).isEqualTo(UPLOAD_DIR + "/" + response.fileName());
        assertThat(contentTypeCaptor.getValue()).isEqualTo("image/jpeg");
    }

    @Test
    void store_disallowedExtension_throwsBadRequestWithoutCallingStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> storageService.store(file)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void store_noExtension_throwsBadRequestWithoutCallingStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "noextension", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> storageService.store(file)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void store_storageUploadFails_propagatesException() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-bytes".getBytes());
        when(storage.uploadFile(any(byte[].class), anyString(), anyString()))
                .thenThrow(new RuntimeException("S3 unavailable"));

        assertThatThrownBy(() -> storageService.store(file)).isInstanceOf(RuntimeException.class);
    }
}
