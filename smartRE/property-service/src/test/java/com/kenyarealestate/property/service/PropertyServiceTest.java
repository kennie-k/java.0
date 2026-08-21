package com.kenyarealestate.property.service;

import com.kenyarealestate.property.client.VerificationClient;
import com.kenyarealestate.property.dto.CreatePropertyRequest;
import com.kenyarealestate.property.dto.PropertyResponse;
import com.kenyarealestate.property.dto.PropertySearchRequest;
import com.kenyarealestate.property.entity.ListingStatus;
import com.kenyarealestate.property.entity.ListingType;
import com.kenyarealestate.property.entity.Property;
import com.kenyarealestate.property.entity.PropertyImageHash;
import com.kenyarealestate.property.entity.PropertyType;
import com.kenyarealestate.property.exception.ConflictException;
import com.kenyarealestate.property.exception.NotFoundException;
import com.kenyarealestate.property.repository.PropertyImageHashRepository;
import com.kenyarealestate.property.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertyServiceTest {

    @Mock private PropertyRepository repo;
    @Mock private VerificationClient verifClient;
    @Mock private RedisTemplate<String, Object> redis;
    @Mock private PropertyAuditService auditService;
    @Mock private PropertyImageHashRepository imageHashRepo;
    @Mock private ImageHashService imageHashService;
    @Mock private ValueOperations<String, Object> valueOps;

    private PropertyService propertyService;

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
                .viewCount(0)
                .build();

        // Constructed manually (rather than @InjectMocks) so the @Value-injected fields
        // (detailTtl/searchTtl/viewDebounceWindowMinutes) have real defaults instead of 0.
        propertyService = new PropertyService(repo, verifClient, redis, auditService, imageHashRepo, imageHashService);
        setField("detailTtl", 300L);
        setField("searchTtl", 120L);
        setField("viewDebounceWindowMinutes", 30L);

        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    private void setField(String name, Object value) {
        try {
            var f = PropertyService.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(propertyService, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CreatePropertyRequest baseCreateRequest() {
        var req = new CreatePropertyRequest();
        req.setTitle("2 Bedroom Apartment in Kilimani");
        req.setPropertyType("APARTMENT");
        req.setListingType("SALE");
        req.setCounty("Nairobi");
        req.setPrice(BigDecimal.valueOf(8000000));
        return req;
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

        var res = propertyService.create(sellerId, baseCreateRequest());

        assertEquals("DRAFT", res.getStatus());
    }

    @Test
    void create_setsPendingVerification_whenSellerIdentityVerified() {
        when(verifClient.isIdentityVerified(sellerId)).thenReturn(true);
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = propertyService.create(sellerId, baseCreateRequest());

        assertEquals("PENDING_VERIFICATION", res.getStatus());
    }

    @Test
    void getActiveBySeller_returnsOnlyThatSellersActiveListings() {
        when(repo.findBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE))
                .thenReturn(List.of(property));

        var results = propertyService.getActiveBySeller(sellerId);

        assertEquals(1, results.size());
        verify(repo).findBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE);
    }

    @Test
    void markOwnershipVerified_activatesListing_whenIdentityAlreadyVerifiedAndParcelUnique() {
        property.setSellerIdentityVerified(true);
        property.setStatus(ListingStatus.PENDING_VERIFICATION);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.findByParcelNumberAndIdNot("LR12345", propertyId)).thenReturn(List.of());
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.markOwnershipVerified(propertyId, "LR12345", "TD-9988");

        assertEquals(ListingStatus.ACTIVE, property.getStatus());
        assertFalse(property.isDuplicateParcelFlag());
        assertEquals("LR12345", property.getParcelNumber());
    }

    @Test
    void markOwnershipVerified_blocksActivation_whenParcelNumberIsDuplicate() {
        property.setSellerIdentityVerified(true);
        property.setStatus(ListingStatus.PENDING_VERIFICATION);
        Property otherListing = Property.builder().id(UUID.randomUUID()).sellerId(otherSellerId).build();
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.findByParcelNumberAndIdNot("LR12345", propertyId)).thenReturn(List.of(otherListing));
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.markOwnershipVerified(propertyId, "LR12345", "TD-9988");

        assertEquals(ListingStatus.PENDING_VERIFICATION, property.getStatus());
        assertTrue(property.isDuplicateParcelFlag());
        verify(auditService).log(eq(propertyId), eq("DUPLICATE_PARCEL_DETECTED"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void markOwnershipVerified_staysPending_whenIdentityNotYetVerified() {
        property.setSellerIdentityVerified(false);
        property.setStatus(ListingStatus.PENDING_VERIFICATION);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.findByParcelNumberAndIdNot("LR12345", propertyId)).thenReturn(List.of());
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.markOwnershipVerified(propertyId, "LR12345", "TD-9988");

        assertEquals(ListingStatus.PENDING_VERIFICATION, property.getStatus());
        assertTrue(property.isPropertyOwnershipVerified());
    }

    @Test
    void markOwnershipVerified_throwsWhenPropertyMissing() {
        when(repo.findById(propertyId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> propertyService.markOwnershipVerified(propertyId, "LR12345", "TD-9988"));
    }

    @Test
    void adminSuspend_setsStatusSuspended() {
        UUID adminId = UUID.randomUUID();
        property.setStatus(ListingStatus.ACTIVE);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = propertyService.adminSuspend(propertyId, adminId);

        assertEquals("SUSPENDED", res.getStatus());
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

        assertEquals("ACTIVE", res.getStatus());
        assertFalse(property.isDuplicateParcelFlag());
        verify(auditService).log(eq(propertyId), eq("ADMIN_REACTIVATED"), any(), any(), eq(adminId), eq("ADMIN"), any(), any());
    }

    @Test
    void adminSuspend_throwsWhenPropertyMissing() {
        UUID adminId = UUID.randomUUID();
        when(repo.findById(propertyId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> propertyService.adminSuspend(propertyId, adminId));
    }

    // ---- search() ----------------------------------------------------------------------

    @Test
    void search_normalizesEnumFiltersToUppercase_andMapsResultsToResponses() {
        PropertySearchRequest req = new PropertySearchRequest();
        req.setCounty("Nairobi");
        req.setPropertyType("apartment");
        req.setListingType("sale");
        req.setPage(0);
        req.setSize(20);

        Page<Property> page = new PageImpl<>(List.of(property));
        when(repo.search(eq("Nairobi"), isNull(), eq("APARTMENT"), eq("SALE"),
                isNull(), isNull(), isNull(), isNull(), eq(false), any(Pageable.class)))
                .thenReturn(page);

        Page<PropertyResponse> result = propertyService.search(req);

        assertEquals(1, result.getTotalElements());
        assertEquals(propertyId, result.getContent().get(0).getId());
        verify(repo).search(eq("Nairobi"), isNull(), eq("APARTMENT"), eq("SALE"),
                isNull(), isNull(), isNull(), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void search_passesThroughPriceBedroomKeywordAndVerifiedOnlyFilters() {
        PropertySearchRequest req = new PropertySearchRequest();
        req.setMinPrice(BigDecimal.valueOf(1_000_000));
        req.setMaxPrice(BigDecimal.valueOf(5_000_000));
        req.setMinBedrooms(2);
        req.setKeyword("westlands");
        req.setVerifiedOnly(true);

        when(repo.search(isNull(), isNull(), isNull(), isNull(),
                eq(BigDecimal.valueOf(1_000_000)), eq(BigDecimal.valueOf(5_000_000)),
                eq(2), eq("westlands"), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<PropertyResponse> result = propertyService.search(req);

        assertEquals(0, result.getTotalElements());
        verify(repo).search(isNull(), isNull(), isNull(), isNull(),
                eq(BigDecimal.valueOf(1_000_000)), eq(BigDecimal.valueOf(5_000_000)),
                eq(2), eq("westlands"), eq(true), any(Pageable.class));
    }

    // ---- activateAllForSeller() ---------------------------------------------------------

    @Test
    void activateAllForSeller_activatesEachListingAccordingToItsOwnOwnershipStatus() {
        Property draftNoOwnership = Property.builder().id(UUID.randomUUID()).sellerId(sellerId)
                .status(ListingStatus.DRAFT).propertyOwnershipVerified(false).build();
        Property pendingWithOwnership = Property.builder().id(UUID.randomUUID()).sellerId(sellerId)
                .status(ListingStatus.PENDING_VERIFICATION).propertyOwnershipVerified(true).build();
        when(repo.findBySellerIdAndStatus(sellerId, ListingStatus.DRAFT))
                .thenReturn(List.of(draftNoOwnership));
        when(repo.findBySellerIdAndStatus(sellerId, ListingStatus.PENDING_VERIFICATION))
                .thenReturn(List.of(pendingWithOwnership));
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        propertyService.activateAllForSeller(sellerId);

        assertTrue(draftNoOwnership.isSellerIdentityVerified());
        assertEquals(ListingStatus.PENDING_VERIFICATION, draftNoOwnership.getStatus());
        assertTrue(pendingWithOwnership.isSellerIdentityVerified());
        assertEquals(ListingStatus.ACTIVE, pendingWithOwnership.getStatus());
        verify(auditService, times(2)).log(any(), eq("IDENTITY_VERIFIED_ACTIVATION"),
                any(), any(), eq(sellerId), eq("SYSTEM"), any(), any());
    }

    @Test
    void activateAllForSeller_doesNotMutateListingsBelongingToOtherStatuses() {
        when(repo.findBySellerIdAndStatus(sellerId, ListingStatus.DRAFT)).thenReturn(List.of());
        when(repo.findBySellerIdAndStatus(sellerId, ListingStatus.PENDING_VERIFICATION)).thenReturn(List.of());

        propertyService.activateAllForSeller(sellerId);

        verify(repo, never()).save(any(Property.class));
        verify(auditService, never()).log(any(), eq("IDENTITY_VERIFIED_ACTIVATION"), any(), any(), any(), any(), any(), any());
    }

    // ---- duplicate-photo fraud check (syncImageHashes via create/update) ----------------

    @Test
    void create_blocksAndAudits_whenPhotoAlreadyUsedByAnotherSeller() {
        String imageUrl = "https://smartre-documents.s3.amazonaws.com/documents/property_image/abc.jpg";
        when(verifClient.isIdentityVerified(sellerId)).thenReturn(false);
        when(repo.save(any(Property.class))).thenAnswer(inv -> {
            Property p = inv.getArgument(0);
            p.setId(propertyId);
            return p;
        });
        when(imageHashService.computeSha256(imageUrl)).thenReturn(Optional.of("deadbeef"));
        PropertyImageHash existing = PropertyImageHash.builder()
                .id(UUID.randomUUID()).propertyId(UUID.randomUUID()).sellerId(otherSellerId)
                .imageUrl(imageUrl).imageHash("deadbeef").build();
        when(imageHashRepo.findFirstByImageHashAndSellerIdNot("deadbeef", sellerId))
                .thenReturn(Optional.of(existing));

        var req = baseCreateRequest();
        req.setImageUrls(List.of(imageUrl));

        assertThrows(ConflictException.class, () -> propertyService.create(sellerId, req));

        verify(auditService).log(eq(propertyId), eq("DUPLICATE_PHOTO_FRAUD_DETECTED"),
                any(), any(), eq(sellerId), eq("SELLER"), any(), any());
        verify(imageHashRepo, never()).saveAll(anyList());
    }

    @Test
    void create_allowsPhoto_whenNoOtherSellerHasUsedIt() {
        String imageUrl = "https://smartre-documents.s3.amazonaws.com/documents/property_image/xyz.jpg";
        when(verifClient.isIdentityVerified(sellerId)).thenReturn(false);
        when(repo.save(any(Property.class))).thenAnswer(inv -> {
            Property p = inv.getArgument(0);
            p.setId(propertyId);
            return p;
        });
        when(imageHashService.computeSha256(imageUrl)).thenReturn(Optional.of("cafebabe"));
        when(imageHashRepo.findFirstByImageHashAndSellerIdNot("cafebabe", sellerId))
                .thenReturn(Optional.empty());

        var req = baseCreateRequest();
        req.setImageUrls(List.of(imageUrl));

        var res = propertyService.create(sellerId, req);

        assertNotNull(res);
        verify(imageHashRepo).saveAll(argThat(list -> {
            List<PropertyImageHash> hashes = new ArrayList<>();
            list.forEach(hashes::add);
            return hashes.size() == 1 && hashes.get(0).getImageHash().equals("cafebabe");
        }));
        verify(auditService, never()).log(any(), eq("DUPLICATE_PHOTO_FRAUD_DETECTED"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_skipsHashing_whenImageHashServiceCannotFetch() {
        String imageUrl = "https://untrusted-host.example.com/some/image.jpg";
        when(verifClient.isIdentityVerified(sellerId)).thenReturn(false);
        when(repo.save(any(Property.class))).thenAnswer(inv -> {
            Property p = inv.getArgument(0);
            p.setId(propertyId);
            return p;
        });
        when(imageHashService.computeSha256(imageUrl)).thenReturn(Optional.empty());

        var req = baseCreateRequest();
        req.setImageUrls(List.of(imageUrl));

        var res = propertyService.create(sellerId, req);

        assertNotNull(res);
        verify(imageHashRepo, never()).findFirstByImageHashAndSellerIdNot(any(), any());
    }

    // ---- getById() view-count debounce ---------------------------------------------------

    @Test
    void getById_incrementsViewCount_onlyOncePerViewerWithinDebounceWindow() {
        property.setStatus(ListingStatus.ACTIVE);
        property.setViewCount(5);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true, false);

        propertyService.getById(propertyId, null, false, "1.2.3.4");
        propertyService.getById(propertyId, null, false, "1.2.3.4");

        verify(repo, times(1)).save(any(Property.class));
        assertEquals(6, property.getViewCount());
    }

    @Test
    void getById_incrementsViewCount_separatelyForDifferentViewers() {
        property.setStatus(ListingStatus.ACTIVE);
        property.setViewCount(5);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(repo.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        propertyService.getById(propertyId, UUID.randomUUID(), false, "1.2.3.4");
        propertyService.getById(propertyId, UUID.randomUUID(), false, "5.6.7.8");

        verify(repo, times(2)).save(any(Property.class));
    }

    @Test
    void getById_doesNotIncrement_whenRedisDebounceCheckFails() {
        property.setStatus(ListingStatus.ACTIVE);
        property.setViewCount(5);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis unavailable"));

        var res = propertyService.getById(propertyId, null, false, "1.2.3.4");

        assertEquals(5, res.getViewCount());
        verify(repo, never()).save(any(Property.class));
    }

    @Test
    void getById_throwsNotFound_whenNonOwnerViewsNonActiveListing() {
        property.setStatus(ListingStatus.DRAFT);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));

        assertThrows(NotFoundException.class,
                () -> propertyService.getById(propertyId, otherSellerId, false, "1.2.3.4"));
    }

    @Test
    void getById_allowsOwner_toViewOwnNonActiveListing() {
        property.setStatus(ListingStatus.DRAFT);
        when(repo.findById(propertyId)).thenReturn(Optional.of(property));
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        var res = propertyService.getById(propertyId, sellerId, false, "1.2.3.4");

        assertEquals(propertyId, res.getId());
    }
}
