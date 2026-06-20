package com.examplatform.exam.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded images on the local filesystem under IMAGE_DIR.
 * Served back via GET /exams/images/{filename} in ExamController.
 */
@Slf4j
@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );
    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    @Value("${image.storage.dir:/app/images}")
    private String imageDir;

    @Value("${image.storage.base-url:http://localhost:8082/api/v1/exams/images}")
    private String baseUrl;

    @PostConstruct
    void init() throws IOException {
        Path dir = Paths.get(imageDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.info("Created image storage directory: {}", dir.toAbsolutePath());
        }
    }

    public String store(MultipartFile file) throws IOException {
        validate(file);
        String filename = UUID.randomUUID() + getExt(file.getOriginalFilename());
        return save(filename, file);
    }

    public String storeWithName(MultipartFile file, String nameHint) throws IOException {
        validate(file);
        String filename = sanitize(nameHint) + "_" + UUID.randomUUID().toString().substring(0, 8) + getExt(file.getOriginalFilename());
        return save(filename, file);
    }

    public Path resolve(String filename) {
        return Paths.get(imageDir).resolve(filename).normalize();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct))
            throw new IllegalArgumentException("Only image files are allowed (JPEG, PNG, GIF, WebP, SVG)");
        if (file.getSize() > MAX_SIZE_BYTES)
            throw new IllegalArgumentException("Image must be under 5 MB");
    }

    private String save(String filename, MultipartFile file) throws IOException {
        Path dest = Paths.get(imageDir).resolve(filename);
        Files.copy(file.getInputStream(), dest);
        String url = baseUrl.stripTrailing() + "/" + filename;
        log.info("Saved image: {}", url);
        return url;
    }

    private String getExt(String filename) {
        if (filename == null || !filename.contains(".")) return ".png";
        return filename.substring(filename.lastIndexOf('.'));
    }

    private String sanitize(String name) {
        return name == null ? "img" : name.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }
}
