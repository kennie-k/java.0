package com.kenyarealestate.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class MpesaB2cClient {

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(15_000);
        f.setReadTimeout(30_000);
        return f;
    }
    private final RestTemplate rt = new RestTemplate(timeoutFactory());

    @Value("${mpesa.consumer-key}")
    private String consumerKey;

    @Value("${mpesa.consumer-secret}")
    private String consumerSecret;

    @Value("${mpesa.shortcode}")
    private String shortcode;

    @Value("${mpesa.b2c-url}")
    private String b2cUrl;

    @Value("${mpesa.auth-url}")
    private String authUrl;

    @Value("${mpesa.b2c-callback-url}")
    private String b2cCallbackUrl;

    @Value("${mpesa.callback-secret}")
    private String callbackSecret;

    @Value("${mpesa.b2c-status-query-url}")
    private String statusQueryUrl;

    @Value("${mpesa.b2c-status-callback-url}")
    private String statusCallbackUrl;

    @Value("${mpesa.initiator-name}")
    private String initiatorName;

    @Value("${mpesa.initiator-credential}")
    private String initiatorCredential;

    @Value("${mpesa.security-credential-cert-path}")
    private Resource securityCredentialCert;

    private volatile String cachedEncryptedCredential;

    public record B2cResult(boolean success, String conversationId,
                             String originatorConversationId, String description) {}

    private synchronized String encryptedSecurityCredential() {
        if (cachedEncryptedCredential != null) return cachedEncryptedCredential;
        try (InputStream in = securityCredentialCert.getInputStream()) {
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, cert.getPublicKey());
            byte[] encrypted = cipher.doFinal(initiatorCredential.getBytes(StandardCharsets.UTF_8));
            cachedEncryptedCredential = Base64.getEncoder().encodeToString(encrypted);
            return cachedEncryptedCredential;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to encrypt M-Pesa SecurityCredential — check mpesa.security-credential-cert-path "
                            + "points at a valid Safaricom public certificate (.cer): " + e.getMessage(), e);
        }
    }

    private String getAccessToken() {
        String creds = Base64.getEncoder().encodeToString(
                (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8));
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Basic " + creds);
        var resp = rt.exchange(authUrl, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        return (String) resp.getBody().get("access_token");
    }

    private String normalize(String phone) {
        if (phone == null) return null;
        if (phone.startsWith("0")) return "254" + phone.substring(1);
        if (phone.startsWith("+")) return phone.substring(1);
        return phone;
    }

    public B2cResult payToPhone(String phoneNumber, String amount, String remarks, UUID revenueId) {
        return sendB2c("BusinessPayment", normalize(phoneNumber), amount, remarks, revenueId);
    }

    public B2cResult payToPaybill(String paybillNumber, String accountNumber,
                                   String amount, String remarks, UUID revenueId) {
        return sendB2c("BusinessPayBill", paybillNumber + "|" + accountNumber, amount, remarks, revenueId);
    }

    public B2cResult payToTill(String tillNumber, String amount, String remarks, UUID revenueId) {
        return sendB2c("BusinessBuyGoods", tillNumber, amount, remarks, revenueId);
    }

    private B2cResult sendB2c(String commandId, String partyB, String amount,
                                String remarks, UUID revenueId) {
        try {
            String token = getAccessToken();
            Map<String, Object> body = new HashMap<>();
            body.put("InitiatorName",      initiatorName);
            body.put("SecurityCredential", encryptedSecurityCredential());
            body.put("CommandID",          commandId);
            body.put("Amount",             amount);
            body.put("PartyA",             shortcode);
            body.put("PartyB",             partyB);
            body.put("Remarks",            remarks);
            body.put("QueueTimeOutURL",    b2cCallbackUrl + "/" + callbackSecret);
            body.put("ResultURL",          b2cCallbackUrl + "/" + callbackSecret);
            body.put("Occasion",           "SmartRE-" + revenueId.toString().substring(0, 8).toUpperCase());

            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(token);
            h.setContentType(MediaType.APPLICATION_JSON);

            var resp = rt.exchange(b2cUrl, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
            Map<?, ?> result = resp.getBody();

            if (result != null && "0".equals(String.valueOf(result.get("ResponseCode")))) {
                return new B2cResult(true,
                        (String) result.get("ConversationID"),
                        (String) result.get("OriginatorConversationID"),
                        (String) result.get("ResponseDescription"));
            }
            return new B2cResult(false, null, null, "B2C failed: " + result);
        } catch (Exception e) {
            log.error("M-Pesa B2C error commandId={}: {}", commandId, e.getMessage());
            return new B2cResult(false, null, null, "B2C API error: " + e.getMessage());
        }
    }

    public record StatusQueryResult(boolean accepted, String queryConversationId,
                                     String queryOriginatorConversationId, String description) {}

    /**
     * Fires a TransactionStatusQuery for a payout that never received its result callback.
     * Like B2C payment itself, this is asynchronous: a ResponseCode of 0 here only means
     * Safaricom accepted the query for processing — the actual answer arrives later as a
     * callback to {@code mpesa.b2c-status-callback-url}, correlated back to the original
     * payout via the query's own OriginatorConversationID (see
     * CompanyRevenue.statusQueryConversationId / RevenueController's status-callback endpoint).
     */
    public StatusQueryResult queryTransactionStatus(String originalOriginatorConversationId, UUID revenueId) {
        try {
            String token = getAccessToken();
            Map<String, Object> body = new HashMap<>();
            body.put("Initiator",             initiatorName);
            body.put("SecurityCredential",    encryptedSecurityCredential());
            body.put("CommandID",             "TransactionStatusQuery");
            body.put("OriginatorConversationID", originalOriginatorConversationId);
            body.put("PartyA",                shortcode);
            body.put("IdentifierType",        "4");
            body.put("ResultURL",             statusCallbackUrl + "/" + callbackSecret);
            body.put("QueueTimeOutURL",       statusCallbackUrl + "/" + callbackSecret);
            body.put("Remarks",               "SmartRE stuck payout reconciliation");
            body.put("Occasion",              "SmartRE-" + revenueId.toString().substring(0, 8).toUpperCase());

            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(token);
            h.setContentType(MediaType.APPLICATION_JSON);

            var resp = rt.exchange(statusQueryUrl, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
            Map<?, ?> result = resp.getBody();

            if (result != null && "0".equals(String.valueOf(result.get("ResponseCode")))) {
                return new StatusQueryResult(true,
                        (String) result.get("ConversationID"),
                        (String) result.get("OriginatorConversationID"),
                        (String) result.get("ResponseDescription"));
            }
            return new StatusQueryResult(false, null, null, "TransactionStatusQuery rejected: " + result);
        } catch (Exception e) {
            log.warn("M-Pesa TransactionStatusQuery error for revenueId={}: {}", revenueId, e.getMessage());
            return new StatusQueryResult(false, null, null, "TransactionStatusQuery API error: " + e.getMessage());
        }
    }
}
