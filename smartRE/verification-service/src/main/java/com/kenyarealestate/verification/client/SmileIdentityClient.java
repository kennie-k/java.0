package com.kenyarealestate.verification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class SmileIdentityClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.smile-identity-enabled}")
    private boolean enabled;

    @Value("${services.smile-identity-url}")
    private String apiUrl;

    @Value("${services.smile-identity-partner-id}")
    private String partnerId;

    @Value("${services.smile-identity-api-key}")
    private String apiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public record BiometricVerificationResult(
            boolean idValid,
            boolean faceMatch,
            double faceMatchConfidence,
            String idNumber,
            String fullName,
            String dateOfBirth,
            String resultCode,
            String resultText
    ) {}

    public BiometricVerificationResult verifyNationalId(String idNumber,
                                                          String selfieImageUrl,
                                                          String idFrontImageUrl) {
        if (!enabled) {
            log.warn("Smile Identity disabled — skipping biometric check");
            return new BiometricVerificationResult(false, false, 0.0,
                    null, null, null, "PENDING",
                    "Biometric verification disabled — requires manual review");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "partner_id", partnerId,
                    "api_key", apiKey,
                    "id_type", "NATIONAL_ID",
                    "country", "KE",
                    "selfie_image", selfieImageUrl,
                    "id_image", idFrontImageUrl
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl + "/id_verification",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<?, ?> result = response.getBody();
            if (result == null) return failed("Empty response from Smile Identity");

            Object actions = result.get("Actions");
            boolean idVerified = false;
            boolean faceMatched = false;
            double confidence = 0.0;

            if (actions instanceof Map<?, ?> actionMap) {
                idVerified  = "Verified".equals(actionMap.get("Verify_ID_Number"));
                faceMatched = "Passed".equals(actionMap.get("Compare_Face_To_ID"));
                Object conf = actionMap.get("Face_Match_Confidence");
                if (conf instanceof Number n) confidence = n.doubleValue();
            }

            return new BiometricVerificationResult(
                    idVerified, faceMatched, confidence,
                    toString(result.get("id_number")),
                    toString(result.get("full_name")),
                    toString(result.get("dob")),
                    toString(result.get("ResultCode")),
                    toString(result.get("ResultText"))
            );
        } catch (Exception e) {
            log.error("Smile Identity API error: {}", e.getMessage());
            return failed("Smile Identity API unavailable: " + e.getMessage());
        }
    }

    private BiometricVerificationResult failed(String reason) {
        return new BiometricVerificationResult(false, false, 0.0,
                null, null, null, "ERROR", reason);
    }

    private String toString(Object v) {
        return v != null ? v.toString() : null;
    }
}
