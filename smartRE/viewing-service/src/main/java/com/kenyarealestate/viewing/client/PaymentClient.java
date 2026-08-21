package com.kenyarealestate.viewing.client;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class PaymentClient {

    private final RestTemplate rt = new RestTemplate();

    @Value("${services.payment-url:http://payment-service:8085}")
    private String paymentUrl;

    @Value("${services.internal-secret:smartre-internal-secret-2026}")
    private String internalSecret;

    @Data
    public static class PaymentInitResult {
        private UUID id;
        private String status;
        private String mpesaCheckoutRequestId;
    }

    public PaymentInitResult initiateViewingFee(UUID buyerId, UUID sellerId,
                                                  UUID propertyId, UUID viewingId,
                                                  String buyerPhone) {
        try {
            Map<String, Object> body = Map.of(
                    "propertyId",     propertyId.toString(),
                    "sellerId",       sellerId.toString(),
                    "amount",         200,
                    "phoneNumber",    buyerPhone,
                    "paymentType",    "VIEWING_FEE",
                    "idempotencyKey", "vf:" + viewingId.toString()
            );

            HttpHeaders h = new HttpHeaders();
            h.set("X-Auth-UserId",     buyerId.toString());
            h.set("X-Auth-Role",       "BUYER");
            h.set("X-Internal-Secret", internalSecret);
            h.setContentType(MediaType.APPLICATION_JSON);

            var resp = rt.exchange(
                    paymentUrl + "/api/payments/initiate",
                    HttpMethod.POST,
                    new HttpEntity<>(body, h),
                    PaymentInitResult.class);

            return resp.getBody();
        } catch (Exception e) {
            log.error("Failed to initiate viewing fee for viewing {}: {}", viewingId, e.getMessage());
            return null;
        }
    }

    public boolean refundViewingFee(UUID paymentId, String reason) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("X-Internal-Secret", internalSecret);

            String url = paymentUrl + "/api/payments/internal/" + paymentId
                    + "/refund-viewing-fee?reason=" + java.net.URLEncoder.encode(reason, java.nio.charset.StandardCharsets.UTF_8);

            rt.exchange(url, HttpMethod.PUT, new HttpEntity<>(h), Void.class);
            return true;
        } catch (Exception e) {
            log.error("Failed to refund viewing fee for payment {}: {}", paymentId, e.getMessage());
            return false;
        }
    }
}
