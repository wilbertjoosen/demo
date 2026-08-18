package com.example.media.ports;

public interface StoragePort {

    String uploadFile(byte[] fileData, String fileName, String contentType);
    boolean deleteFile(String url);
}
