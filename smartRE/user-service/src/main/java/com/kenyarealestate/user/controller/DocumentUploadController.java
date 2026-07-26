package com.kenyarealestate.user.controller;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private static final Pattern SAFE_CATEGORY = Pattern.compile("^[A-Za-z0-9_-]+$");

    @Value("${storage.local-dir:/app/uploads}")
    private String localDir;

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    @Value("${s3.enabled:false}")
    private boolean s3Enabled;

    @Value("${s3.endpoint:https://s3.amazonaws.com}")
    private String s3Endpoint;

    @Value("${s3.region:us-east-1}")
    private String s3Region;

    @Value("${s3.bucket:smartre-documents}")
    private String bucket;

    @Value("${s3.access-key:placeholder}")
    private String accessKey;

    @Value("${s3.secret-key:placeholder}")
    private String secretKey;

    @Value("${s3.public-base-url:https://smartre-documents.s3.amazonaws.com}")
    private String s3PublicBaseUrl;

    private S3Client s3Client;

    @PostConstruct
    void init() {
        if (!s3Enabled) return;

        if ("placeholder".equals(accessKey) || "placeholder".equals(secretKey)) {
            throw new IllegalStateException(
                    "s3.enabled=true but S3_ACCESS_KEY/S3_SECRET_KEY are still the placeholder default. " +
                    "Set real credentials, or leave S3_ENABLED=false to use local disk storage instead.");
        }

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.of(s3Region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))

                .forcePathStyle(true)
                .build();

        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 storage enabled - verified access to bucket '{}' at {}", bucket, s3Endpoint);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "s3.enabled=true but bucket '" + bucket + "' at " + s3Endpoint +
                    " is not reachable with the configured credentials: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }

        if (!SAFE_CATEGORY.matcher(category).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid category"));
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

        try {
            String url = s3Enabled ? uploadToS3(objectKey, file) : saveLocally(objectKey, file);
            log.info("Document stored ({}): category={} key={}", s3Enabled ? "S3" : "local disk", category, objectKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "url", url,
                    "objectKey", objectKey,
                    "category", category,
                    "sizeBytes", file.getSize()
            ));
        } catch (Exception e) {
            log.error("Upload failed for key={}: {}", objectKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String key = path.substring("/files/".length());

        Path root = Paths.get(localDir).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(contentTypeFor(target.toString()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(new FileSystemResource(target));
    }

    private String saveLocally(String key, MultipartFile file) throws Exception {
        Path root = Paths.get(localDir).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        return publicUrl + "/api/documents/files/" + key;
    }

    private String uploadToS3(String key, MultipartFile file) throws Exception {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                        .build(),
                RequestBody.fromBytes(file.getBytes()));
        return s3PublicBaseUrl + "/" + key;
    }

    private MediaType contentTypeFor(String filename) {
        return switch (getExtension(filename)) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
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
