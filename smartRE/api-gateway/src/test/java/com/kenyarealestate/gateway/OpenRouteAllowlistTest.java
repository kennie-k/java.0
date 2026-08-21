package com.kenyarealestate.gateway;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class OpenRouteAllowlistTest {

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
            List<Object> predicates = (List<Object>) route.get("predicates");
            if (predicates == null) continue;

            for (Object predicate : predicates) {
                String p = String.valueOf(predicate);
                if (p.contains("/internal/")) {
                    violations.add("Route '" + id + "' matches internal path: " + p
                            + " - internal endpoints must never be exposed on the public API Gateway.");
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("API Gateway must never expose internal endpoints:\n" + String.join("\n", violations));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void gatewayStripsInternalSecretHeaderFromIncomingRequests() throws Exception {
        Yaml yaml = new Yaml();
        InputStream in = getClass().getClassLoader().getResourceAsStream("application.yaml");
        assertTrue(in != null, "application.yaml must be present on the test classpath");

        Map<String, Object> root = yaml.load(in);
        Map<String, Object> spring = (Map<String, Object>) root.get("spring");
        Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
        Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
        List<Object> defaultFilters = (List<Object>) gateway.get("default-filters");

        assertTrue(defaultFilters != null && defaultFilters.stream()
                .anyMatch(f -> String.valueOf(f).contains("RemoveRequestHeader=X-Internal-Secret")),
                "default-filters in application.yaml must contain RemoveRequestHeader=X-Internal-Secret to sanitize incoming requests.");
    }
}
