package com.kenyarealestate.verification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

@Slf4j
@Service
public class DocumentAnalysisService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.document-analysis-enabled}")
    private boolean analysisEnabled;

    public boolean isEnabled() {
        return analysisEnabled;
    }

    @Value("${services.document-analysis-url}")
    private String analysisUrl;

    @Value("${services.document-analysis-key}")
    private String analysisKey;

    @Value("${services.user-service-url}")
    private String userServiceUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    @Value("${services.document-url-allowed-hosts:localhost,user-service}")
    private String documentUrlAllowedHostsCsv;


    @Value("${services.ocr-model-url}")
    private String ocrModelUrl;

    @Value("${services.ocr-model-name:qwen3.6:latest}")
    private String ocrModelName;

    public record DocumentAnalysisResult(
            String sha256Hash,
            Integer authenticityScore,
            boolean tamperDetected,
            boolean alterationDetected,
            boolean metadataClean,
            boolean fontConsistency,
            boolean signatureDetected,
            boolean sealDetected,
            boolean dateSequenceValid,
            String notes
    ) {}

    public String computeSha256FromUrl(String documentUrl) {
        try {
            assertUrlIsSafeToFetch(documentUrl);
            String fetchUrl = resolveFetchUrl(documentUrl);
            URI uri = URI.create(fetchUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            if (fetchUrl.contains("/api/documents/internal/files/")) {
                conn.setRequestProperty("X-Internal-Secret", internalSecret);
            }
            try (InputStream in = conn.getInputStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    md.update(buf, 0, read);
                }
                return HexFormat.of().formatHex(md.digest());
            }
        } catch (Exception e) {
            log.error("Failed to compute SHA-256 for document URL {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }

    private String resolveFetchUrl(String documentUrl) {
        int idx = documentUrl.indexOf("/api/documents/files/");
        if (idx == -1) return documentUrl;
        String suffix = documentUrl.substring(idx + "/api/documents/".length());
        return userServiceUrl + "/api/documents/internal/" + suffix;
    }

    /**
     * Guards against SSRF: documentUrl is client-supplied (it comes straight from the upload
     * request DTO), and this service fetches it server-side to hash/analyse it. URLs that point
     * at user-service's own document endpoint are safe regardless of their claimed host, because
     * resolveFetchUrl() discards the host entirely and always re-targets the trusted internal
     * user-service URL. Anything else (e.g. an S3 URL, or an attacker-supplied URL aimed at an
     * internal service or the cloud metadata endpoint) must resolve to an explicitly allowed
     * host, or it's rejected before any network call is made.
     */
    private void assertUrlIsSafeToFetch(String documentUrl) {
        if (!StringUtils.hasText(documentUrl)) {
            throw new IllegalArgumentException("Document URL is blank");
        }
        if (documentUrl.contains("/api/documents/files/")) {
            return;
        }
        URI uri = URI.create(documentUrl);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Document URL must use http or https");
        }
        String host = uri.getHost();
        if (host == null || !allowedHosts().contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Document URL host '" + host + "' is not an approved document storage host");
        }
    }

    private Set<String> allowedHosts() {
        return Arrays.stream(documentUrlAllowedHostsCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(h -> h.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public DocumentAnalysisResult analyseDocument(String documentUrl, String documentCategory) {
        try {
            assertUrlIsSafeToFetch(documentUrl);
        } catch (Exception e) {
            log.warn("Refusing to analyse document with disallowed URL: {}", e.getMessage());
            return pendingAnalysis(null);
        }
        if (!analysisEnabled) {
            return pendingAnalysis(computeSha256FromUrl(documentUrl));
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", analysisKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of(
                    "documentUrl", documentUrl,
                    "documentType", documentCategory
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    analysisUrl + "/analyse",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<?, ?> result = response.getBody();
            if (result == null) return pendingAnalysis(computeSha256FromUrl(documentUrl));

            return new DocumentAnalysisResult(
                    computeSha256FromUrl(documentUrl),
                    toInt(result.get("authenticityScore")),
                    toBool(result.get("tamperDetected")),
                    toBool(result.get("alterationDetected")),
                    toBool(result.get("metadataClean")),
                    toBool(result.get("fontConsistency")),
                    toBool(result.get("signatureDetected")),
                    toBool(result.get("sealDetected")),
                    toBool(result.get("dateSequenceValid")),
                    toString(result.get("notes"))
            );
        } catch (Exception e) {
            log.error("Document analysis API call failed for {}: {}", documentUrl, e.getMessage());
            return pendingAnalysis(computeSha256FromUrl(documentUrl));
        }
    }

    private DocumentAnalysisResult pendingAnalysis(String hash) {
        return new DocumentAnalysisResult(hash, null, false, false,
                false, false, false, false, false,
                "Automated analysis pending — requires manual review");
    }

    private Integer toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        return false;
    }

    private String toString(Object v) {
        return v != null ? v.toString() : null;
    }


    private static final Pattern KENYAN_ID_PATTERN = Pattern.compile("^\\d{7,8}$");

    /**
     * Single-purpose OCR call: extracts just the ID number from a document image.
     * Deliberately separate from analyseDocument() above — asking a vision model to
     * do tamper detection, font analysis, AND precise field extraction in one prompt
     * degrades extraction accuracy. This does one thing, with a narrow response
     * format, so failures are easy to detect (UNREADABLE, or a non-matching shape)
     * rather than silently wrong.
     *
     * Callers should only invoke this for document categories known to actually
     * carry an ID number (see ID_NUMBER_EXTRACTABLE in SellerIdentityVerificationService)
     * — this method does not itself gate by category.
     *
     * Returns null if extraction wasn't attempted, the model couldn't read the
     * document, or the result doesn't look like a real Kenyan ID number — in all
     * of those cases, the caller should route the document to human review rather
     * than trust a low-confidence guess.
     *
     * @param mimeType the document's actual stored mime type (SellerIdentityDocument.mimeType,
     *                 captured and validated against magic bytes at upload time) — NOT trusted
     *                 blindly here either; only used to decide whether PDF rasterization is needed,
     *                 the resulting bytes are always re-encoded as PNG regardless of the claim.
     */
    public String extractIdNumber(String documentUrl, String mimeType) {
        if (!analysisEnabled) {
            log.info("Document analysis disabled - skipping ID number extraction for {}", documentUrl);
            return null;
        }
        try {

            assertUrlIsSafeToFetch(documentUrl);
        } catch (Exception e) {
            log.warn("Refusing to OCR document with disallowed URL: {}", e.getMessage());
            return null;
        }

        try {

            URI uri = URI.create(fetchUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            if (fetchUrl.contains("/api/documents/internal/files/")) {
                conn.setRequestProperty("X-Internal-Secret", internalSecret);
            }
            byte[] rawBytes;
            try (InputStream in = conn.getInputStream()) {
                rawBytes = in.readAllBytes();
            }

            byte[] pngBytes = isPdf(mimeType, rawBytes)
                    ? rasterizeFirstPdfPageToPng(rawBytes, documentUrl)
                    : reencodeAsPng(rawBytes, documentUrl);

            if (pngBytes == null) return null;

            String base64Image = Base64.getEncoder().encodeToString(pngBytes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model", ocrModelName,
                    "temperature", 0,
                    "max_tokens", 20,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "You extract a single field from Kenyan identity documents. " +
                                            "Respond with ONLY the raw ID number as plain text, digits only, " +
                                            "no labels, no punctuation, no explanation. If no ID number is " +
                                            "clearly visible or legible, respond with exactly: UNREADABLE"
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of("type", "text", "text", "Extract the ID number from this document."),

                                            Map.of("type", "image_url", "image_url",
                                                    Map.of("url", "data:image/png;base64," + base64Image))
                                    )
                            )
                    )
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    ocrModelUrl + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            String raw = extractChatCompletionText(response.getBody());
            if (raw == null) return null;

            String cleaned = raw.trim();
            if ("UNREADABLE".equalsIgnoreCase(cleaned)) {
                log.info("OCR reported unreadable ID number for {}", documentUrl);
                return null;
            }
            if (!KENYAN_ID_PATTERN.matcher(cleaned).matches()) {
                log.warn("OCR result '{}' doesn't match Kenyan ID number format - discarding, routing to human review", cleaned);
                return null;
            }
            return cleaned;

        } catch (Exception e) {
            log.error("ID number extraction failed for {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractChatCompletionText(Map<?, ?> responseBody) {
        if (responseBody == null) return null;
        try {
            var choices = (List<Map<String, Object>>) responseBody.get("choices");
            var message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.warn("Unexpected chat completion response shape: {}", e.getMessage());
            return null;
        }
    }


    private boolean isPdf(String mimeType, byte[] rawBytes) {
        boolean claimsPdf = mimeType != null && mimeType.equalsIgnoreCase("application/pdf");
        boolean looksLikePdf = rawBytes.length >= 4
                && rawBytes[0] == '%' && rawBytes[1] == 'P' && rawBytes[2] == 'D' && rawBytes[3] == 'F';
        if (claimsPdf != looksLikePdf) {
            log.warn("mimeType/magic-byte mismatch for document (claims PDF: {}, looks like PDF: {}) - trusting magic bytes",
                    claimsPdf, looksLikePdf);
        }
        return looksLikePdf;
    }


    private byte[] rasterizeFirstPdfPageToPng(byte[] pdfBytes, String documentUrl) {
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            if (pdf.getNumberOfPages() == 0) {
                log.warn("PDF has no pages: {}", documentUrl);
                return null;
            }
            PDFRenderer renderer = new PDFRenderer(pdf);
            var pageImage = renderer.renderImageWithDPI(0, 200);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(pageImage, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to rasterize PDF for OCR extraction, document {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }


    private byte[] reencodeAsPng(byte[] rawBytes, String documentUrl) {
        try {
            var image = ImageIO.read(new java.io.ByteArrayInputStream(rawBytes));
            if (image == null) {
                log.warn("Could not decode document as an image (unsupported or corrupt format): {}", documentUrl);
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to re-encode image for OCR extraction, document {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }
}