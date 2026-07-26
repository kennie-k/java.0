package com.kenyarealestate.gateway;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class OpenRouteAllowlistTest {

    private static final Set<String> KNOWN_SAFE_INTERNAL_ROUTES = Set.of(
            "verif-identity-internal-open",
            "verif-ownership-internal-open",
            "property-internal-open",
            "viewing-internal-open"
    );

    @SuppressWarnings("unchecked")
    @Test
    void noOpenRouteExposesAnInternalEndpoint() throws Exception {
        Yaml yaml = new Yaml();
        InputStream in = getClass().getClassLoader().getResourceAsStream("application.yaml");
        assertTrue(in != null, "application.yaml must be present on the test classpath");

        Map<String, Object> root = yaml.load(in);
        Map<String, Object> spring = (Map<String, Object>) root.get("spring");
        Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
        Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
        List<Map<String, Object>> routes = (List<Map<String, Object>>) gateway.get("routes");

        List<String> violations = new ArrayList<>();

        for (Map<String, Object> route : routes) {
            String id = String.valueOf(route.get("id"));
            List<Object> filters = (List<Object>) route.get("filters");
            boolean hasAuthFilter = filters != null && filters.stream()
                    .anyMatch(f -> String.valueOf(f).contains("AuthFilter"));

            if (hasAuthFilter) continue;

            List<Object> predicates = (List<Object>) route.get("predicates");
            if (predicates == null) continue;

            if (KNOWN_SAFE_INTERNAL_ROUTES.contains(id)) continue;

            for (Object predicate : predicates) {
                String p = String.valueOf(predicate);
                if (p.contains("/internal/")) {
                    violations.add("Route '" + id + "' is open (no AuthFilter) but matches an internal path: " + p
                            + " - if this is a legitimate service-to-service route, add a downstream "
                            + "InternalSecretFilter and add its id to KNOWN_SAFE_INTERNAL_ROUTES.");
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Open routes must never expose an internal endpoint:\n" + String.join("\n", violations));
        }
    }
}
