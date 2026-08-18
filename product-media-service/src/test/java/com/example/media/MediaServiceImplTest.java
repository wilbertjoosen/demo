package com.example.media;

import com.example.media.enums.MediaType;
import com.example.media.model.MediaAsset;
import com.example.media.ports.StoragePort;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.service.MediaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    private static final String UPLOAD_DIR = "media";

    @Mock
    MediaAssetRepository mediaAssetRepository;

    @Mock
    StoragePort storage;

    MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(UPLOAD_DIR, mediaAssetRepository, storage);
    }

    @Test
    void create_firstAssetForProduct_getsPositionZero() {
        when(mediaAssetRepository.countByProductIdAndDeletedFalse("p1")).thenReturn(0);
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        mediaService.create("p1", MediaType.PHOTO, "http://example.com/img.jpg", "img.jpg", "caption");

        verify(mediaAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getPosition()).isEqualTo(0);
    }

    @Test
    void create_secondAssetForProduct_appendsAtNextPosition() {
        when(mediaAssetRepository.countByProductIdAndDeletedFalse("p1")).thenReturn(2);
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        mediaService.create("p1", MediaType.VIDEO, "http://example.com/v.mp4", "v.mp4", "caption");

        verify(mediaAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getPosition()).isEqualTo(2);
    }

    @Test
    void create_storesUploadDirAsAssetPath() {
        when(mediaAssetRepository.countByProductIdAndDeletedFalse("p1")).thenReturn(0);
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        mediaService.create("p1", MediaType.PHOTO, "http://example.com/img.jpg", "img.jpg", "caption");

        verify(mediaAssetRepository).save(captor.capture());
        assertThat(captor.getValue().getPath()).isEqualTo(UPLOAD_DIR);
        assertThat(captor.getValue().getFileMame()).isEqualTo("img.jpg");
    }

    @Test
    void listByProduct_returnsOrderedAssets() {
        List<MediaAsset> assets = List.of(new MediaAsset("p1", MediaType.PHOTO, "u1", "c1", UPLOAD_DIR, "caption", 0));
        when(mediaAssetRepository.findByProductIdAndDeletedFalseOrderByPositionAsc("p1")).thenReturn(assets);

        assertThat(mediaService.listByProduct("p1")).isEqualTo(assets);
    }

    @Test
    void delete_found_marksDeletedAndRemovesFromStorage() {
        MediaAsset asset = new MediaAsset("p1", MediaType.PHOTO, "u1", "c1.jpg", UPLOAD_DIR, "caption", 0);
        when(mediaAssetRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(asset));

        mediaService.delete("m1");

        assertThat(asset.isDeleted()).isTrue();
        verify(storage).deleteFile(UPLOAD_DIR + "/c1.jpg");
        verify(mediaAssetRepository).save(asset);
    }

    @Test
    void delete_notFound_throwsNotFoundWithoutTouchingStorage() {
        when(mediaAssetRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.delete("missing")).isInstanceOf(ResponseStatusException.class);
        verify(mediaAssetRepository, never()).save(any());
        verifyNoInteractions(storage);
    }
}
