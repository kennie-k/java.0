package com.kenyarealestate.user.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    @Value("${s3.enabled:false}")
    private boolean s3Enabled;

    @Value("${s3.endpoint:https://s3.amazonaws.com}")
    private String s3Endpoint;

    @Value("${s3.bucket:smartre-documents}")
    private String bucket;

    @Value("${s3.access-key:placeholder}")
    private String accessKey;

    @Value("${s3.secret-key:placeholder}")
    private String secretKey;

    @Value("${s3.public-base-url:https://smartre-documents.s3.amazonaws.com}")
    private String publicBaseUrl;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }

        String ext = getExtension(file.getOriginalFilename());
        if (!isAllowedExtension(ext)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File type not allowed. Use PDF, JPG, or PNG."));
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File exceeds 10MB limit"));
        }

        try {
            byte[] header = new byte[8];
            try (InputStream headerStream = file.getInputStream()) {
                int read = headerStream.read(header);
                if (read < 4 || !matchesDeclaredType(header, ext)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "File content does not match its extension. The file may be corrupted or mislabeled."));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read file: " + e.getMessage()));
        }

        String objectKey = "documents/" + category.toLowerCase() + "/" + UUID.randomUUID() + "." + ext;

        if (!s3Enabled) {

            String testUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a7/" +
                    "Camponotus_flavomarginatus_ant.jpg/640px-Camponotus_flavomarginatus_ant.jpg" +
                    "?dev-fingerprint=" + objectKey;
            log.info("S3 disabled (dev mode) — returning test URL for category={}", category);
            return ResponseEntity.ok(Map.of(
                    "url", testUrl,
                    "objectKey", objectKey,
                    "category", category,
                    "sizeBytes", file.getSize(),
                    "note", "Development mode: real file not stored, using test URL"
            ));
        }

        try {

            String url = uploadToS3(objectKey, file);
            log.info("Document uploaded: category={} key={}", category, objectKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "url", url,
                    "objectKey", objectKey,
                    "category", category,
                    "sizeBytes", file.getSize()
            ));
        } catch (Exception e) {
            log.error("S3 upload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    private String uploadToS3(String key, MultipartFile file) throws Exception {

        String uploadUrl = s3Endpoint + "/" + bucket + "/" + key;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", file.getContentType() != null
                        ? file.getContentType() : "application/octet-stream")
                .header("Authorization", "Bearer " + accessKey + ":" + secretKey)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();
        client.send(request, HttpResponse.BodyHandlers.discarding());
        return publicBaseUrl + "/" + key;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean isAllowedExtension(String ext) {
        return ext.equals("pdf") || ext.equals("jpg") ||
                ext.equals("jpeg") || ext.equals("png");
    }

    private boolean matchesDeclaredType(byte[] header, String ext) {
        boolean isJpeg = header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
        boolean isPng = header.length >= 4
                && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
        boolean isPdf = header.length >= 4
                && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46;

        return switch (ext) {
            case "jpg", "jpeg" -> isJpeg;
            case "png" -> isPng;
            case "pdf" -> isPdf;
            default -> false;
        };
    }
}