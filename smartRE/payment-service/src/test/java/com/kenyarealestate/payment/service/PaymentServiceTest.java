package com.kenyarealestate.payment.service;

import com.kenyarealestate.payment.client.MpesaClient;
import com.kenyarealestate.payment.client.PropertyServiceClient;
import com.kenyarealestate.payment.dto.MpesaCallbackRequest;
import com.kenyarealestate.payment.entity.Payment;
import com.kenyarealestate.payment.entity.PaymentStatus;
import com.kenyarealestate.payment.entity.PaymentType;
import com.kenyarealestate.payment.kafka.PaymentEventPublisher;
import com.kenyarealestate.payment.repository.MpesaRawCallbackRepository;
import com.kenyarealestate.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository repo;
    @Mock private MpesaClient mpesaClient;
    @Mock private PaymentLockService lockService;
    @Mock private PaymentEventPublisher eventPublisher;
    @Mock private PaymentAuditService auditService;
    @Mock private MpesaRawCallbackRepository rawCallbackRepo;
    @Mock private ReceiptService receiptService;
    @Mock private org.springframework.data.redis.core.RedisTemplate<String, Object> redis;
    @Mock private PropertyServiceClient propertyServiceClient;
    @Mock private RevenueService revenueService;

    @InjectMocks private PaymentService paymentService;

    private UUID buyerId;
    private UUID sellerId;
    private UUID strangerId;
    private UUID paymentId;
    private UUID propertyId;
    private Payment payment;

    @BeforeEach
    void setup() {
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        payment = Payment.builder()
                .id(paymentId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(15000000))
                .phoneNumber("254708374149")
                .build();
    }

    @Test
    void getAuditTrail_allowsBuyer() {
        when(repo.findById(paymentId)).thenReturn(Optional.of(payment));

        paymentService.getAuditTrail(paymentId, buyerId);
    }

    @Test
    void getAuditTrail_allowsSeller() {
        when(repo.findById(paymentId)).thenReturn(Optional.of(payment));

        paymentService.getAuditTrail(paymentId, sellerId);
    }

    @Test
    void getAuditTrail_deniesUnrelatedUser() {
        when(repo.findById(paymentId)).thenReturn(Optional.of(payment));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> paymentService.getAuditTrail(paymentId, strangerId));

        assertEquals("Access denied", ex.getMessage());
    }

    @Test
    void getReceipt_deniesUnrelatedUser() {
        when(repo.findById(paymentId)).thenReturn(Optional.of(payment));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> paymentService.getReceipt(paymentId, strangerId));

        assertEquals("Access denied", ex.getMessage());
    }

    @Test
    void getReceipt_allowsSeller() {
        when(repo.findById(paymentId)).thenReturn(Optional.of(payment));

        paymentService.getReceipt(paymentId, sellerId);
    }

    @Test
    void getByIdAndBuyer_deniesNonBuyer() {
        when(repo.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThrows(RuntimeException.class, () -> paymentService.getByIdAndBuyer(paymentId, sellerId));
    }

    @Test
    void hasProfileAccess_returnsFalseWhenNoGrant() {
        when(redis.hasKey("profile:access:" + buyerId + ":" + sellerId)).thenReturn(false);

        boolean result = paymentService.hasProfileAccess(buyerId, sellerId);

        org.junit.jupiter.api.Assertions.assertFalse(result);
    }

    @Test
    void hasProfileAccess_returnsTrueAfterGrant() {
        when(redis.hasKey("profile:access:" + buyerId + ":" + sellerId)).thenReturn(true);

        boolean result = paymentService.hasProfileAccess(buyerId, sellerId);

        org.junit.jupiter.api.Assertions.assertTrue(result);
    }

    // ── handleCallback: idempotency / duplicate-callback handling ───────────

    private MpesaCallbackRequest callbackRequest(String checkoutId, int resultCode, String receipt, String amount, String phone) {
        MpesaCallbackRequest req = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback cb = new MpesaCallbackRequest.StkCallback();
        cb.setCheckoutRequestId(checkoutId);
        cb.setResultCode(resultCode);
        cb.setResultDesc(resultCode == 0 ? "The service request is processed successfully." : "Cancelled by user");
        if (resultCode == 0) {
            MpesaCallbackRequest.CallbackMetadata meta = new MpesaCallbackRequest.CallbackMetadata();
            MpesaCallbackRequest.Item receiptItem = new MpesaCallbackRequest.Item();
            receiptItem.setName("MpesaReceiptNumber");
            receiptItem.setValue(receipt);
            MpesaCallbackRequest.Item amountItem = new MpesaCallbackRequest.Item();
            amountItem.setName("Amount");
            amountItem.setValue(amount);
            MpesaCallbackRequest.Item phoneItem = new MpesaCallbackRequest.Item();
            phoneItem.setName("PhoneNumber");
            phoneItem.setValue(phone);
            MpesaCallbackRequest.Item dateItem = new MpesaCallbackRequest.Item();
            dateItem.setName("TransactionDate");
            dateItem.setValue("20260807120000");
            meta.setItems(List.of(receiptItem, amountItem, phoneItem, dateItem));
            cb.setCallbackMetadata(meta);
        }
        body.setStkCallback(cb);
        req.setBody(body);
        return req;
    }

    @Test
    void handleCallback_success_marksCompletedAndPublishesEventExactlyOnce() {
        String checkoutId = "ws_CO_1";
        Payment stkPushed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.STK_PUSHED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId(checkoutId).build();
        when(repo.findByMpesaCheckoutRequestIdForUpdate(checkoutId)).thenReturn(Optional.of(stkPushed));

        MpesaCallbackRequest req = callbackRequest(checkoutId, 0, "REC123", "1000", "254708374149");
        paymentService.handleCallback(req, "{}");

        assertEquals(PaymentStatus.COMPLETED, stkPushed.getStatus());
        assertEquals("REC123", stkPushed.getMpesaReceiptNumber());
        verify(eventPublisher, times(1)).recordAndPublish(stkPushed);
        verify(receiptService, times(1)).issueReceipt(eq(stkPushed), any(), any(), anyString(), eq("MPESA"), any());
    }

    @Test
    void handleCallback_duplicateCallbackOnAlreadyCompletedPayment_isIgnoredAndDoesNotRepublish() {
        String checkoutId = "ws_CO_2";
        Payment completed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId(checkoutId).mpesaReceiptNumber("REC123").build();
        when(repo.findByMpesaCheckoutRequestIdForUpdate(checkoutId)).thenReturn(Optional.of(completed));

        MpesaCallbackRequest req = callbackRequest(checkoutId, 0, "REC123", "1000", "254708374149");
        paymentService.handleCallback(req, "{}");

        // Duplicate callback on an already-COMPLETED payment must not re-trigger the
        // review-unlock event or a second receipt.
        verify(eventPublisher, never()).recordAndPublish(any());
        verify(receiptService, never()).issueReceipt(any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    void handleCallback_duplicateCallbackOnFailedPayment_isIgnored() {
        String checkoutId = "ws_CO_3";
        Payment failed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.FAILED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId(checkoutId).build();
        when(repo.findByMpesaCheckoutRequestIdForUpdate(checkoutId)).thenReturn(Optional.of(failed));

        MpesaCallbackRequest req = callbackRequest(checkoutId, 0, "REC999", "1000", "254708374149");
        paymentService.handleCallback(req, "{}");

        assertEquals(PaymentStatus.FAILED, failed.getStatus());
        verify(eventPublisher, never()).recordAndPublish(any());
    }

    @Test
    void handleCallback_amountMismatch_marksFailedAndDoesNotPublish() {
        String checkoutId = "ws_CO_4";
        Payment stkPushed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.STK_PUSHED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId(checkoutId).build();
        when(repo.findByMpesaCheckoutRequestIdForUpdate(checkoutId)).thenReturn(Optional.of(stkPushed));

        // Callback reports a wildly different amount than what was requested.
        MpesaCallbackRequest req = callbackRequest(checkoutId, 0, "REC123", "5", "254708374149");
        paymentService.handleCallback(req, "{}");

        assertEquals(PaymentStatus.FAILED, stkPushed.getStatus());
        verify(eventPublisher, never()).recordAndPublish(any());
    }

    @Test
    void handleCallback_unknownCheckoutId_doesNothing() {
        when(repo.findByMpesaCheckoutRequestIdForUpdate("unknown")).thenReturn(Optional.empty());

        MpesaCallbackRequest req = callbackRequest("unknown", 0, "REC1", "1000", "254708374149");
        paymentService.handleCallback(req, "{}");

        verify(eventPublisher, never()).recordAndPublish(any());
        verify(repo, never()).save(any());
    }

    // ── reconcileOne: STK status-query reconciliation ───────────────────────

    @Test
    void reconcileOne_succeededQuery_marksCompletedAndPublishesEvent() {
        Payment stkPushed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.STK_PUSHED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId("ws_CO_5").build();
        when(repo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(stkPushed));
        when(mpesaClient.queryStkStatus("ws_CO_5"))
                .thenReturn(new MpesaClient.StkQueryResult(true, true, "0", "Success"));

        paymentService.reconcileOne(paymentId, false);

        assertEquals(PaymentStatus.COMPLETED, stkPushed.getStatus());
        verify(eventPublisher, times(1)).recordAndPublish(stkPushed);
    }

    @Test
    void reconcileOne_failedQuery_marksFailed() {
        Payment stkPushed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.STK_PUSHED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId("ws_CO_6").build();
        when(repo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(stkPushed));
        when(mpesaClient.queryStkStatus("ws_CO_6"))
                .thenReturn(new MpesaClient.StkQueryResult(true, false, "1032", "Cancelled by user"));

        paymentService.reconcileOne(paymentId, false);

        assertEquals(PaymentStatus.FAILED, stkPushed.getStatus());
        verify(eventPublisher, never()).recordAndPublish(any());
    }

    @Test
    void reconcileOne_stillProcessing_leavesPaymentUnchangedUnlessTimedOut() {
        Payment stkPushed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.STK_PUSHED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId("ws_CO_7").build();
        when(repo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(stkPushed));
        when(mpesaClient.queryStkStatus("ws_CO_7"))
                .thenReturn(new MpesaClient.StkQueryResult(false, false, "500.001.1001", "still processing"));

        paymentService.reconcileOne(paymentId, false);

        assertEquals(PaymentStatus.STK_PUSHED, stkPushed.getStatus());
        verify(eventPublisher, never()).recordAndPublish(any());
    }

    @Test
    void reconcileOne_stillProcessingButTimedOut_marksFailed() {
        Payment stkPushed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.STK_PUSHED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId("ws_CO_8").build();
        when(repo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(stkPushed));
        when(mpesaClient.queryStkStatus("ws_CO_8"))
                .thenReturn(new MpesaClient.StkQueryResult(false, false, "500.001.1001", "still processing"));

        paymentService.reconcileOne(paymentId, true);

        assertEquals(PaymentStatus.FAILED, stkPushed.getStatus());
    }

    @Test
    void reconcileOne_alreadyCompleted_isNoOp() {
        Payment completed = Payment.builder()
                .id(paymentId).buyerId(buyerId).sellerId(sellerId).propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT).status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(1000)).phoneNumber("254708374149")
                .mpesaCheckoutRequestId("ws_CO_9").build();
        when(repo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(completed));

        paymentService.reconcileOne(paymentId, false);

        verify(mpesaClient, never()).queryStkStatus(anyString());
        verify(eventPublisher, never()).recordAndPublish(any());
    }
}
