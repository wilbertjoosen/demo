package com.example.media.validation;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Real magic-byte content sniffing, not just trusting the claimed extension/Content-Type — the
 * whole reason MediaValidationListener exists is that a presigned upload's actual bytes never pass
 * through this service, so nothing else here ever inspects them. Covers the same extensions
 * MediaFileStorageService's old direct-upload path already allow-lists (single shared source of
 * truth: {@link #ALLOWED_EXTENSIONS}).
 *
 * <p>Not exhaustive for every video container variant (mp4/mov checks are container-atom based,
 * not a full ISO-BMFF parse) — good enough to catch a mismatched or spoofed file type, not a
 * substitute for a real media-forensics library.
 */
@Component
public class MediaContentValidator {

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "webm", "mov",
            "pdf", "doc", "docx", "xls", "xlsx");

    /** Minimum header size that lets every signature check below actually run. */
    public static final int HEADER_BYTES_NEEDED = 16;

    public boolean matchesExtension(String extension, byte[] header) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47);
            case "gif" -> startsWith(header, 0x47, 0x49, 0x46, 0x38);
            case "webp" -> startsWith(header, 0x52, 0x49, 0x46, 0x46) && containsAsciiAt(header, 8, "WEBP");
            case "pdf" -> startsWith(header, 0x25, 0x50, 0x44, 0x46);
            // Legacy binary Office format — same OLE compound-file signature for both .doc and .xls.
            case "doc", "xls" -> startsWith(header, 0xD0, 0xCF, 0x11, 0xE0);
            // Office Open XML (.docx/.xlsx) is a ZIP archive under the hood.
            case "docx", "xlsx" -> startsWith(header, 0x50, 0x4B, 0x03, 0x04);
            case "mp4" -> containsAsciiAt(header, 4, "ftyp");
            case "mov" -> containsAsciiAt(header, 4, "ftyp") || containsAsciiAt(header, 4, "moov")
                    || containsAsciiAt(header, 4, "free") || containsAsciiAt(header, 4, "wide");
            case "webm" -> startsWith(header, 0x1A, 0x45, 0xDF, 0xA3);
            default -> false;
        };
    }

    private boolean startsWith(byte[] header, int... expectedUnsignedBytes) {
        if (header.length < expectedUnsignedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedUnsignedBytes.length; i++) {
            if ((header[i] & 0xFF) != expectedUnsignedBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAsciiAt(byte[] header, int offset, String ascii) {
        byte[] expected = ascii.getBytes(StandardCharsets.US_ASCII);
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
