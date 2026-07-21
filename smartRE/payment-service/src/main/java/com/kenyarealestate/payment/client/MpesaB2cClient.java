package com.kenyarealestate.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class MpesaB2cClient {

    private final RestTemplate rt = new RestTemplate();

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

    @Value("${mpesa.initiator-name}")
    private String initiatorName;

    @Value("${mpesa.initiator-credential}")
    private String initiatorCredential;

    public record B2cResult(boolean success, String conversationId,
                             String originatorConversationId, String description) {}

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
            body.put("SecurityCredential", initiatorCredential);
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
}
