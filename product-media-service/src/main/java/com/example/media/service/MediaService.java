package com.example.media.service;

import com.example.media.enums.MediaType;
import com.example.media.model.MediaAsset;

import java.util.List;

public interface MediaService {

    MediaAsset create(String productId, MediaType type, String url, String fileName, String caption);

    List<MediaAsset> listByProduct(String productId);

    void delete(String id);
}
