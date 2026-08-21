package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.client.PropertyServiceClient;
import com.kenyarealestate.verification.entity.SellerIdentityVerification;
import com.kenyarealestate.verification.entity.VerificationFraudFlag;
import com.kenyarealestate.verification.repository.PropertyOwnershipVerificationRepository;
import com.kenyarealestate.verification.repository.SellerIdentityVerificationRepository;
import com.kenyarealestate.verification.repository.VerificationFraudFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the fraud-strike counter and the permanent-ban threshold, including the downstream
 * effects (Redis flag + property-service suspension + audit log) that fire once a seller
 * crosses max-fraud-strikes.
 */
@ExtendWith(MockitoExtension.class)
class FraudFlagServiceTest {

    @Mock private VerificationFraudFlagRepository fraudRepo;
    @Mock private SellerIdentityVerificationRepository identityRepo;
    @Mock private PropertyOwnershipVerificationRepository ownershipRepo;
    @Mock private AuditService auditService;
    @Mock private RedisTemplate<String, Object> redis;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private PropertyServiceClient propertyServiceClient;

    private FraudFlagService service;

    @BeforeEach
    void setup() {
        service = new FraudFlagService(fraudRepo, identityRepo, ownershipRepo, auditService, redis, propertyServiceClient);
        ReflectionTestUtils.setField(service, "maxFraudStrikes", 3);
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void flagIdentityFraud_savesFlagAndIncrementsStrikeCount() {
        UUID verificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SellerIdentityVerification verif = SellerIdentityVerification.builder()
                .id(verificationId).userId(userId).fraudStrikeCount(1).build();
        when(identityRepo.findById(verificationId)).thenReturn(Optional.of(verif));

        int strikes = service.flagIdentityFraud(verificationId, userId, "DOCUMENT_TAMPER_DETECTED",
                "NATIONAL_ID_FRONT", "somehash", "AI detected tampering");

        assertEquals(2, strikes);
        assertEquals(2, verif.getFraudStrikeCount());
        verify(fraudRepo).save(any(VerificationFraudFlag.class));
        verify(identityRepo).save(verif);
    }

    @Test
    void applyIdentityBanIfNeeded_doesNothing_belowThreshold() {
        UUID verificationId = UUID.randomUUID();

        service.applyIdentityBanIfNeeded(verificationId, 2);

        verifyNoInteractions(identityRepo, redis, propertyServiceClient, auditService);
    }

    @Test
    void applyIdentityBanIfNeeded_bansAndNotifiesDownstream_atThreshold() {
        UUID verificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SellerIdentityVerification verif = SellerIdentityVerification.builder()
                .id(verificationId).userId(userId).fraudStrikeCount(3).build();
        when(identityRepo.findById(verificationId)).thenReturn(Optional.of(verif));

        service.applyIdentityBanIfNeeded(verificationId, 3);

        assertTrue(verif.isPermanentlyBanned());
        assertNotNull(verif.getBanReason());
        verify(identityRepo).save(verif);
        verify(valueOperations).set("user:banned:" + userId, "true");
        verify(propertyServiceClient).suspendAllListingsForSeller(eq(userId), any());
        verify(auditService).log(eq(verificationId), eq("IDENTITY"), eq("ACCOUNT_PERMANENTLY_BANNED"),
                any(), eq("SYSTEM"), any(), eq("BANNED"), any());
    }

    @Test
    void applyIdentityBanIfNeeded_bansAboveThresholdToo() {
        UUID verificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SellerIdentityVerification verif = SellerIdentityVerification.builder()
                .id(verificationId).userId(userId).fraudStrikeCount(5).build();
        when(identityRepo.findById(verificationId)).thenReturn(Optional.of(verif));

        service.applyIdentityBanIfNeeded(verificationId, 5);

        assertTrue(verif.isPermanentlyBanned());
    }

    @Test
    void flagOwnershipFraud_savesFlagAndIncrementsVerificationStrikeCount() {
        UUID verificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var verif = com.kenyarealestate.verification.entity.PropertyOwnershipVerification.builder()
                .id(verificationId).fraudStrikeCount(0).build();
        when(ownershipRepo.findById(verificationId)).thenReturn(Optional.of(verif));

        int strikes = service.flagOwnershipFraud(verificationId, userId, "DUPLICATE_DOCUMENT_HASH",
                "TITLE_DEED", "somehash", "duplicate detected");

        assertEquals(1, strikes);
        assertEquals(1, verif.getFraudStrikeCount());
        verify(fraudRepo).save(any(VerificationFraudFlag.class));
        verify(ownershipRepo).save(verif);
    }
}
