package com.kenyarealestate.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@Slf4j @Component
public class MpesaClient {
    private final RestTemplate rt = new RestTemplate();

    @Value("${mpesa.consumer-key}")    private String consumerKey;
    @Value("${mpesa.consumer-secret}") private String consumerSecret;
    @Value("${mpesa.shortcode}")       private String shortcode;
    @Value("${mpesa.passkey}")         private String passkey;
    @Value("${mpesa.callback-url}")    private String callbackUrl;
    @Value("${mpesa.callback-secret}") private String callbackSecret;
    @Value("${mpesa.auth-url}")        private String authUrl;
    @Value("${mpesa.stk-push-url}")    private String stkPushUrl;

    public record StkPushResult(boolean success, String checkoutRequestId, String merchantRequestId, String responseDescription) {}

    private String getAccessToken() {
        String creds = Base64.getEncoder().encodeToString((consumerKey+":"+consumerSecret).getBytes(StandardCharsets.UTF_8));
        HttpHeaders h = new HttpHeaders(); h.set("Authorization","Basic "+creds);
        var resp = rt.exchange(authUrl, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        return (String) resp.getBody().get("access_token");
    }

    public StkPushResult initiateSTKPush(String phone, String amount, String accountRef, String description) {
        try {
            String token = getAccessToken();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String password = Base64.getEncoder().encodeToString(
                    (shortcode+passkey+timestamp).getBytes(StandardCharsets.UTF_8));
            String normalizedPhone = phone.startsWith("0") ? "254"+phone.substring(1) : phone.startsWith("+") ? phone.substring(1) : phone;

            Map<String,Object> body = new java.util.HashMap<>();
            body.put("BusinessShortCode", shortcode);
            body.put("Password", password);
            body.put("Timestamp", timestamp);
            body.put("TransactionType", "CustomerPayBillOnline");
            body.put("Amount", amount);
            body.put("PartyA", normalizedPhone);
            body.put("PartyB", shortcode);
            body.put("PhoneNumber", normalizedPhone);
            body.put("CallBackURL", callbackUrl + "/" + callbackSecret);
            body.put("AccountReference", accountRef);
            body.put("TransactionDesc", description);

            HttpHeaders h = new HttpHeaders(); h.setBearerAuth(token); h.setContentType(MediaType.APPLICATION_JSON);
            var resp = rt.exchange(stkPushUrl, HttpMethod.POST, new HttpEntity<>(body,h), Map.class);
            Map<?,?> result = resp.getBody();

            if (result!=null && "0".equals(String.valueOf(result.get("ResponseCode")))) {
                return new StkPushResult(true,
                        (String)result.get("CheckoutRequestID"),
                        (String)result.get("MerchantRequestID"),
                        (String)result.get("ResponseDescription"));
            }
            return new StkPushResult(false,null,null,"STK push failed: "+result);
        } catch (Exception e) {
            log.error("M-Pesa STK push error: {}", e.getMessage());
            return new StkPushResult(false,null,null,"M-Pesa API error: "+e.getMessage());
        }
    }
}
