package com.example.media.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaContentValidatorTest {

    private final MediaContentValidator validator = new MediaContentValidator();

    private static byte[] header(int... unsignedBytes) {
        byte[] result = new byte[16];
        for (int i = 0; i < unsignedBytes.length; i++) {
            result[i] = (byte) unsignedBytes[i];
        }
        return result;
    }

    @Test
    void jpeg_realMagicBytes_matches() {
        assertThat(validator.matchesExtension("jpg", header(0xFF, 0xD8, 0xFF, 0xE0))).isTrue();
    }

    @Test
    void png_realMagicBytes_matches() {
        assertThat(validator.matchesExtension("png", header(0x89, 0x50, 0x4E, 0x47))).isTrue();
    }

    @Test
    void pdf_realMagicBytes_matches() {
        assertThat(validator.matchesExtension("pdf", header(0x25, 0x50, 0x44, 0x46))).isTrue();
    }

    @Test
    void docx_isJustAZipArchive_matches() {
        assertThat(validator.matchesExtension("docx", header(0x50, 0x4B, 0x03, 0x04))).isTrue();
    }

    @Test
    void spoofedFile_extensionClaimsJpgButBytesAreAnExecutable_doesNotMatch() {
        // MZ header — a Windows PE executable renamed to .jpg
        assertThat(validator.matchesExtension("jpg", header(0x4D, 0x5A, 0x90, 0x00))).isFalse();
    }

    @Test
    void unknownExtension_neverMatches() {
        assertThat(validator.matchesExtension("exe", header(0xFF, 0xD8, 0xFF))).isFalse();
    }

    @Test
    void headerShorterThanSignature_doesNotMatch() {
        assertThat(validator.matchesExtension("png", new byte[]{(byte) 0x89})).isFalse();
    }
}
