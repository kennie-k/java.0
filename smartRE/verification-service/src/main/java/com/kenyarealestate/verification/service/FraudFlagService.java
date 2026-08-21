package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.client.PropertyServiceClient;
import com.kenyarealestate.verification.entity.PropertyOwnershipVerification;
import com.kenyarealestate.verification.entity.SellerIdentityVerification;
import com.kenyarealestate.verification.entity.VerificationFraudFlag;
import com.kenyarealestate.verification.repository.PropertyOwnershipVerificationRepository;
import com.kenyarealestate.verification.repository.SellerIdentityVerificationRepository;
import com.kenyarealestate.verification.repository.VerificationFraudFlagRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FraudFlagService {

    private final VerificationFraudFlagRepository fraudRepo;
    private final SellerIdentityVerificationRepository identityRepo;
    private final PropertyOwnershipVerificationRepository ownershipRepo;
    private final AuditService auditService;
    private final RedisTemplate<String, Object> redis;
    private final PropertyServiceClient propertyServiceClient;

    private static final String BANNED_USER_PREFIX = "user:banned:";

    @Value("${verification.max-fraud-strikes:3}")
    private int maxFraudStrikes;

    public FraudFlagService(VerificationFraudFlagRepository fraudRepo,
                             SellerIdentityVerificationRepository identityRepo,
                             PropertyOwnershipVerificationRepository ownershipRepo,
                             AuditService auditService,
                             RedisTemplate<String, Object> redis,
                             PropertyServiceClient propertyServiceClient) {
        this.fraudRepo = fraudRepo;
        this.identityRepo = identityRepo;
        this.ownershipRepo = ownershipRepo;
        this.auditService = auditService;
        this.redis = redis;
        this.propertyServiceClient = propertyServiceClient;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int flagIdentityFraud(UUID verificationId, UUID userId, String fraudType,
                                  String docCategory, String docHash, String details) {
        fraudRepo.save(VerificationFraudFlag.builder()
                .userId(userId).verificationId(verificationId)
                .verificationTrack("IDENTITY").fraudType(fraudType)
                .documentCategory(docCategory).documentHash(docHash)
                .details(details).build());
        SellerIdentityVerification verif = identityRepo.findById(verificationId).orElseThrow();
        int strikes = verif.getFraudStrikeCount() + 1;
        verif.setFraudStrikeCount(strikes);
        identityRepo.save(verif);
        return strikes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyIdentityBanIfNeeded(UUID verificationId, int strikes) {
        if (strikes < maxFraudStrikes) return;
        SellerIdentityVerification verif = identityRepo.findById(verificationId).orElseThrow();
        verif.setPermanentlyBanned(true);
        verif.setBanReason("Permanently banned after " + strikes + " fraud strikes. "
                + "Submitting falsified documents is a criminal offence under Kenya Penal Code Cap 63.");
        identityRepo.save(verif);
        redis.opsForValue().set(BANNED_USER_PREFIX + verif.getUserId(), "true");
        propertyServiceClient.suspendAllListingsForSeller(verif.getUserId(),
                "Seller permanently banned after " + strikes + " fraud strikes");
        auditService.log(verificationId, "IDENTITY", "ACCOUNT_PERMANENTLY_BANNED",
                null, "SYSTEM", null, "BANNED", "Total fraud strikes: " + strikes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int flagOwnershipFraud(UUID verificationId, UUID userId, String fraudType,
                                   String docCategory, String docHash, String details) {
        fraudRepo.save(VerificationFraudFlag.builder()
                .userId(userId).verificationId(verificationId)
                .verificationTrack("OWNERSHIP").fraudType(fraudType)
                .documentCategory(docCategory).documentHash(docHash)
                .details(details).build());
        PropertyOwnershipVerification verif = ownershipRepo.findById(verificationId).orElseThrow();
        int strikes = verif.getFraudStrikeCount() + 1;
        verif.setFraudStrikeCount(strikes);
        ownershipRepo.save(verif);
        return strikes;
    }
}
