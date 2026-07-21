package com.kenyarealestate.payment.dto;
import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data;
@Data public class MpesaCallbackRequest {
    @JsonProperty("Body") private Body body;
    @Data public static class Body {
        @JsonProperty("stkCallback") private StkCallback stkCallback;
    }
    @Data public static class StkCallback {
        @JsonProperty("MerchantRequestID") private String merchantRequestId;
        @JsonProperty("CheckoutRequestID") private String checkoutRequestId;
        @JsonProperty("ResultCode")        private Integer resultCode;
        @JsonProperty("ResultDesc")        private String resultDesc;
        @JsonProperty("CallbackMetadata")  private CallbackMetadata callbackMetadata;
    }
    @Data public static class CallbackMetadata {
        @JsonProperty("Item") private java.util.List<Item> items;
    }
    @Data public static class Item {
        @JsonProperty("Name")  private String name;
        @JsonProperty("Value") private Object value;
    }
}
