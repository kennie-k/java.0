package com.kenyarealestate.verification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

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
        if (!analysisEnabled) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(documentUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(md.digest());
            } catch (Exception e) {
                log.error("Failed to compute hash from URL string: {}", e.getMessage());
                return java.util.UUID.randomUUID().toString().replace("-", "");
            }
        }
        try {
            URI uri = URI.create(documentUrl);
            try (InputStream in = uri.toURL().openStream()) {
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

    public DocumentAnalysisResult analyseDocument(String documentUrl, String documentCategory) {
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
}