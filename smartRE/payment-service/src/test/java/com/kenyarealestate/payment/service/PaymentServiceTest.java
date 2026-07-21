package com.kenyarealestate.payment.service;

import com.kenyarealestate.payment.client.MpesaClient;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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

    @InjectMocks private PaymentService paymentService;

    private UUID buyerId;
    private UUID sellerId;
    private UUID strangerId;
    private UUID paymentId;
    private Payment payment;

    @BeforeEach
    void setup() {
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        payment = Payment.builder()
                .id(paymentId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .propertyId(UUID.randomUUID())
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
}
