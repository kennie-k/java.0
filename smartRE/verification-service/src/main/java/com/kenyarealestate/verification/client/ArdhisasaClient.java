package com.kenyarealestate.verification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class ArdhisasaClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.ardhisasa-enabled}")
    private boolean enabled;

    @Value("${services.ardhisasa-url}")
    private String apiUrl;

    @Value("${services.ardhisasa-api-key}")
    private String apiKey;

    public record LandSearchResult(
            boolean found,
            boolean titleValid,
            boolean encumbranceClear,
            String registeredOwner,
            String parcelNumber,
            String titleDeedNumber,
            String county,
            String area,
            String landUse,
            boolean hasActiveCaveats,
            boolean hasActiveCharges,
            boolean hasCourtOrders,
            String resultMessage
    ) {}

    public LandSearchResult searchParcel(String parcelNumber, String county) {
        if (!enabled) {
            log.warn("Ardhisasa not enabled — returning pending result for parcel {}", parcelNumber);
            return new LandSearchResult(false, false, false,
                    null, parcelNumber, null, county, null, null,
                    false, false, false,
                    "Ministry of Lands check pending — requires manual Ardhisasa verification");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of(
                    "parcel_number", parcelNumber,
                    "county", county
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl + "/parcels/search",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<?, ?> result = response.getBody();
            if (result == null) return pending(parcelNumber, county);

            boolean hasActiveCaveats = toBool(result.get("has_caveats"));
            boolean hasActiveCharges = toBool(result.get("has_charges"));
            boolean hasCourtOrders   = toBool(result.get("has_court_orders"));
            boolean encumbranceClear = !hasActiveCaveats && !hasActiveCharges && !hasCourtOrders;

            return new LandSearchResult(
                    true,
                    toBool(result.get("title_valid")),
                    encumbranceClear,
                    toString(result.get("registered_owner")),
                    toString(result.get("parcel_number")),
                    toString(result.get("title_deed_number")),
                    toString(result.get("county")),
                    toString(result.get("area")),
                    toString(result.get("land_use")),
                    hasActiveCaveats,
                    hasActiveCharges,
                    hasCourtOrders,
                    toString(result.get("message"))
            );
        } catch (Exception e) {
            log.error("Ardhisasa API error for parcel {}: {}", parcelNumber, e.getMessage());
            return pending(parcelNumber, county);
        }
    }

    public LandSearchResult searchByTitleDeed(String titleDeedNumber) {
        if (!enabled) {
            return new LandSearchResult(false, false, false,
                    null, null, titleDeedNumber, null, null, null,
                    false, false, false,
                    "Ardhisasa API not enabled — manual verification required");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", apiKey);
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl + "/titles/" + titleDeedNumber,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            Map<?, ?> result = response.getBody();
            if (result == null) return pending(null, null);

            boolean hasActiveCaveats = toBool(result.get("has_caveats"));
            boolean hasActiveCharges = toBool(result.get("has_charges"));
            boolean hasCourtOrders   = toBool(result.get("has_court_orders"));

            return new LandSearchResult(
                    true, toBool(result.get("title_valid")),
                    !hasActiveCaveats && !hasActiveCharges && !hasCourtOrders,
                    toString(result.get("registered_owner")),
                    toString(result.get("parcel_number")),
                    titleDeedNumber,
                    toString(result.get("county")),
                    toString(result.get("area")),
                    toString(result.get("land_use")),
                    hasActiveCaveats, hasActiveCharges, hasCourtOrders,
                    "Title deed lookup successful"
            );
        } catch (Exception e) {
            log.error("Ardhisasa title search error for {}: {}", titleDeedNumber, e.getMessage());
            return pending(null, null);
        }
    }

    private LandSearchResult pending(String parcel, String county) {
        return new LandSearchResult(false, false, false,
                null, parcel, null, county, null, null,
                false, false, false,
                "Ardhisasa API unavailable — manual verification required");
    }

    private boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        return false;
    }

    private String toString(Object v) {
        return v != null ? v.toString() : null;
    }
}
