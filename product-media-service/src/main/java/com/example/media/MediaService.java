package com.example.media;

import java.util.List;

public interface MediaService {

    MediaAsset create(String productId, MediaType type, String url, String caption);

    List<MediaAsset> listByProduct(String productId);

    void delete(String id);
}
