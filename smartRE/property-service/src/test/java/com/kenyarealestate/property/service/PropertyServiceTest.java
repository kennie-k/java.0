package com.kenyarealestate.property.service;

import com.kenyarealestate.property.client.VerificationClient;
import com.kenyarealestate.property.entity.ListingStatus;
import com.kenyarealestate.property.entity.ListingType;
import com.kenyarealestate.property.entity.Property;
import com.kenyarealestate.property.entity.PropertyType;
import com.kenyarealestate.property.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock private PropertyRepository repo;
    @Mock private VerificationClient verifClient;
    @Mock private RedisTemplate<String, Object> redis;
    @Mock private PropertyAuditService auditService;

    @InjectMocks private PropertyService propertyService;

    private UUID sellerId;
    private UUID otherSellerId;
    private UUID propertyId;
    private Property property;

    @BeforeEach
    void setup() {
        sellerId = UUID.randomUUID();
        otherSellerId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        property = Property.builder()
                .id(propertyId)
                .sellerId(sellerId)
                .title("3 Bedroom House in Westlands")
                .propertyType(PropertyType.HOUSE)
                .listingType(ListingType.SALE)
                .status(ListingStatus.DRAFT)
                .county("Nairobi")
                .price(BigDecimal.valueOf(15000000))
                .build();
    }

    @Test
    void delete_deniesNonOwner() {
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));

        assertThrows(RuntimeException.class, () -> propertyService.delete(propertyId, otherSellerId));
        verify(repo, never()).delete(any(Property.class));
    }

    @Test
    void delete_allowsOwner() {
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));

        propertyService.delete(propertyId, sellerId);

        verify(repo).delete(property);
    }

    @Test
    void delete_throwsWhenPropertyMissing() {
        when(repo.findById(propertyId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> propertyService.delete(propertyId, sellerId));
    }

    @Test
    void create_setsDraftStatus_whenSellerNotIdentityVerified() {
        when(verifClient.isIdentityVerified(sellerId)).thenReturn(false);
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new com.kenyarealestate.property.dto.CreatePropertyRequest();
        req.setTitle("2 Bedroom Apartment in Kilimani");
        req.setPropertyType("APARTMENT");
        req.setListingType("SALE");
        req.setCounty("Nairobi");
        req.setPrice(BigDecimal.valueOf(8000000));

        var res = propertyService.create(sellerId, req);

        org.junit.jupiter.api.Assertions.assertEquals("DRAFT", res.getStatus());
    }

    @Test
    void create_setsPendingVerification_whenSellerIdentityVerified() {
        when(verifClient.isIdentityVerified(sellerId)).thenReturn(true);
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new com.kenyarealestate.property.dto.CreatePropertyRequest();
        req.setTitle("2 Bedroom Apartment in Kilimani");
        req.setPropertyType("APARTMENT");
        req.setListingType("SALE");
        req.setCounty("Nairobi");
        req.setPrice(BigDecimal.valueOf(8000000));

        var res = propertyService.create(sellerId, req);

        org.junit.jupiter.api.Assertions.assertEquals("PENDING_VERIFICATION", res.getStatus());
    }

    @Test
    void getActiveBySeller_returnsOnlyThatSellersActiveListings() {
        when(repo.findBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE))
                .thenReturn(java.util.List.of(property));

        var results = propertyService.getActiveBySeller(sellerId);

        org.junit.jupiter.api.Assertions.assertEquals(1, results.size());
        verify(repo).findBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE);
    }

    @Test
    void markOwnershipVerified_activatesListing_whenIdentityAlreadyVerifiedAndParcelUnique() {
        property.setSellerIdentityVerified(true);
        property.setStatus(ListingStatus.PENDING_VERIFICATION);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.findByParcelNumberAndIdNot("LR12345", propertyId)).thenReturn(java.util.List.of());
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.markOwnershipVerified(propertyId, "LR12345", "TD-9988");

        org.junit.jupiter.api.Assertions.assertEquals(ListingStatus.ACTIVE, property.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(property.isDuplicateParcelFlag());
        org.junit.jupiter.api.Assertions.assertEquals("LR12345", property.getParcelNumber());
    }

    @Test
    void markOwnershipVerified_blocksActivation_whenParcelNumberIsDuplicate() {
        property.setSellerIdentityVerified(true);
        property.setStatus(ListingStatus.PENDING_VERIFICATION);
        Property otherListing = Property.builder().id(UUID.randomUUID()).sellerId(otherSellerId).build();
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.findByParcelNumberAndIdNot("LR12345", propertyId)).thenReturn(java.util.List.of(otherListing));
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.markOwnershipVerified(propertyId, "LR12345", "TD-9988");

        org.junit.jupiter.api.Assertions.assertEquals(ListingStatus.PENDING_VERIFICATION, property.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(property.isDuplicateParcelFlag());
        verify(auditService).log(eq(propertyId), eq("DUPLICATE_PARCEL_DETECTED"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void markOwnershipVerified_staysPending_whenIdentityNotYetVerified() {
        property.setSellerIdentityVerified(false);
        property.setStatus(ListingStatus.PENDING_VERIFICATION);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.findByParcelNumberAndIdNot("LR12345", propertyId)).thenReturn(java.util.List.of());
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.markOwnershipVerified(propertyId, "LR12345", "TD-9988");

        org.junit.jupiter.api.Assertions.assertEquals(ListingStatus.PENDING_VERIFICATION, property.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(property.isPropertyOwnershipVerified());
    }

    @Test
    void markOwnershipVerified_throwsWhenPropertyMissing() {
        when(repo.findById(propertyId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> propertyService.markOwnershipVerified(propertyId, "LR12345", "TD-9988"));
    }

    @Test
    void adminSuspend_setsStatusSuspended() {
        UUID adminId = UUID.randomUUID();
        property.setStatus(ListingStatus.ACTIVE);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = propertyService.adminSuspend(propertyId, adminId);

        org.junit.jupiter.api.Assertions.assertEquals("SUSPENDED", res.getStatus());
        verify(auditService).log(eq(propertyId), eq("ADMIN_SUSPENDED"), any(), any(), eq(adminId), eq("ADMIN"), any(), any());
    }

    @Test
    void adminReactivate_setsStatusActive_andClearsDuplicateFlag() {
        UUID adminId = UUID.randomUUID();
        property.setStatus(ListingStatus.SUSPENDED);
        property.setDuplicateParcelFlag(true);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = propertyService.adminReactivate(propertyId, adminId);

        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", res.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(property.isDuplicateParcelFlag());
        verify(auditService).log(eq(propertyId), eq("ADMIN_REACTIVATED"), any(), any(), eq(adminId), eq("ADMIN"), any(), any());
    }

    @Test
    void adminSuspend_throwsWhenPropertyMissing() {
        UUID adminId = UUID.randomUUID();
        when(repo.findById(propertyId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> propertyService.adminSuspend(propertyId, adminId));
    }
}
