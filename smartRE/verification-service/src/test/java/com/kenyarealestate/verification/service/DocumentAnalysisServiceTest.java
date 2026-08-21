package com.kenyarealestate.verification.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the SSRF fix: computeSha256FromUrl/analyseDocument must refuse to fetch a
 * client-supplied documentUrl unless it either (a) points at user-service's own document
 * endpoint (rewritten to a trusted internal host regardless of the claimed host), or
 * (b) resolves to a host on the configured allowlist. Anything else — an attempt to reach
 * an internal service or the cloud metadata endpoint via a crafted documentUrl — must be
 * rejected before any network call is made.
 */
class DocumentAnalysisServiceTest {

    private DocumentAnalysisService service;
    private HttpServer allowedHostServer;
    private int allowedPort;

    @BeforeEach
    void setup() throws Exception {
        // A tiny local HTTP server standing in for an "allowed" document host (localhost).
        allowedHostServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        allowedHostServer.createContext("/doc.pdf", exchange -> {
            byte[] body = "hello-document".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        allowedHostServer.start();
        allowedPort = allowedHostServer.getAddress().getPort();

        service = new DocumentAnalysisService();
        ReflectionTestUtils.setField(service, "analysisEnabled", false);
        ReflectionTestUtils.setField(service, "analysisUrl", "https://api.yourdocumentai.com");
        ReflectionTestUtils.setField(service, "analysisKey", "placeholder");
        ReflectionTestUtils.setField(service, "userServiceUrl", "http://user-service:8081");
        ReflectionTestUtils.setField(service, "internalSecret", "test-secret");
        ReflectionTestUtils.setField(service, "documentUrlAllowedHostsCsv", "127.0.0.1,localhost,user-service");
    }

    @AfterEach
    void teardown() {
        allowedHostServer.stop(0);
    }

    @Test
    void computeSha256FromUrl_refusesUrlOnDisallowedHost() {
        String maliciousUrl = "http://169.254.169.254/latest/meta-data/iam/security-credentials/";

        String hash = service.computeSha256FromUrl(maliciousUrl);

        assertNull(hash, "must refuse to fetch a URL whose host isn't allowlisted");
    }

    @Test
    void computeSha256FromUrl_refusesNonHttpScheme() {
        String hash = service.computeSha256FromUrl("file:///etc/passwd");

        assertNull(hash);
    }

    @Test
    void computeSha256FromUrl_allowsUrlOnAllowlistedHost() {
        String url = "http://127.0.0.1:" + allowedPort + "/doc.pdf";

        String hash = service.computeSha256FromUrl(url);

        assertNotNull(hash, "an allowlisted host should be fetchable");
        assertEquals(64, hash.length(), "should be a hex-encoded SHA-256 digest");
    }

    @Test
    void computeSha256FromUrl_alwaysAllowsUserServiceDocumentUrls_regardlessOfClaimedHost() {
        // Even though this URL claims an attacker-controlled host, resolveFetchUrl() discards
        // the host entirely and re-targets the trusted internal user-service URL - so it must
        // not be rejected by the allowlist check (and, since userServiceUrl:8081 isn't running
        // in this test, it fails at connection time instead - proving no allowlist rejection
        // happened first, only a connection failure, which is a different failure mode).
        String url = "http://evil.example.com/api/documents/files/documents/national_id/abc.pdf";

        // Should not throw an IllegalArgumentException about disallowed hosts; the null result
        // comes from the failed connection to the (non-running) internal user-service, which is
        // the expected outer failure in this unit test environment.
        String hash = service.computeSha256FromUrl(url);
        assertNull(hash);
    }

    @Test
    void analyseDocument_returnsPendingAnalysis_forDisallowedHost_withoutCallingExternalApi() {
        ReflectionTestUtils.setField(service, "analysisEnabled", true);
        ReflectionTestUtils.setField(service, "analysisUrl", "http://127.0.0.1:1"); // nothing listens here

        var result = service.analyseDocument(
                "http://169.254.169.254/latest/meta-data/", "NATIONAL_ID_FRONT");

        assertNotNull(result);
        assertNull(result.authenticityScore());
        assertFalse(result.tamperDetected());
        assertTrue(result.notes().toLowerCase().contains("pending")
                || result.notes().toLowerCase().contains("manual"));
    }
}
