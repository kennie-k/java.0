package com.kenyarealestate.property.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Fetches a property image and computes its SHA-256 hash, used by PropertyService for
 * cross-seller duplicate-photo fraud detection. Kept separate from PropertyService so the
 * outbound fetch — and in particular its SSRF allowlisting — can be unit tested and reasoned
 * about in isolation.
 *
 * SECURITY: imageUrl is user-supplied (submitted at property create/update time). Before this
 * fix, any URL was fetched server-side with no host validation, which let a caller point this
 * service's outbound request at arbitrary internal hosts (cloud metadata endpoints, other
 * internal services, etc.) via a create/update request. Now:
 *  - URLs that reference user-service's document-serving path are rewritten to always target
 *    the trusted internal user-service host (the attacker-supplied host in the original URL is
 *    discarded entirely — only the path suffix is kept), exactly as before.
 *  - Any other URL is only fetched if its host matches the configured allowlist (the public
 *    gateway host and/or the S3 public bucket host that user-service actually issues upload
 *    URLs from). Anything else is refused and treated as "no hash available" rather than fetched.
 */
@Slf4j
@Service
public class ImageHashService {

    @Value("${services.user-service-url}")
    private String userServiceUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    @Value("${services.gateway-public-url:http://localhost:8080}")
    private String gatewayPublicUrl;

    @Value("${services.s3-public-base-url:https://smartre-documents.s3.amazonaws.com}")
    private String s3PublicBaseUrl;

    public Optional<String> computeSha256(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return Optional.empty();
        try {
            String fetchUrl = resolveFetchUrl(imageUrl);
            boolean rewrittenToTrustedInternalHost = !fetchUrl.equals(imageUrl);
            if (!rewrittenToTrustedInternalHost && !isAllowedHost(fetchUrl)) {
                log.warn("Refusing to fetch image from a non-allowlisted host for duplicate-photo check: {}", imageUrl);
                return Optional.empty();
            }

            URI uri = URI.create(fetchUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            if (fetchUrl.contains("/api/documents/internal/files/")) {
                conn.setRequestProperty("X-Internal-Secret", internalSecret);
            }
            try (InputStream in = conn.getInputStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    md.update(buf, 0, read);
                }
                return Optional.of(java.util.HexFormat.of().formatHex(md.digest()));
            }
        } catch (Exception e) {
            log.warn("Could not fetch property image for duplicate check, skipping: {} ({})", imageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    // Mirrors DocumentAnalysisService's resolveFetchUrl in verification-service: imageUrl is
    // built at upload time from the browser-facing public host, which isn't reachable from
    // inside this container — route through user-service's internal-secret-gated endpoint
    // instead. Note the target host here is always the configured, trusted userServiceUrl —
    // only the path suffix comes from the (otherwise untrusted) input imageUrl.
    private String resolveFetchUrl(String imageUrl) {
        int idx = imageUrl.indexOf("/api/documents/files/");
        if (idx == -1) return imageUrl;
        String suffix = imageUrl.substring(idx + "/api/documents/".length());
        return userServiceUrl + "/api/documents/internal/" + suffix;
    }

    /** SSRF guard: only follow http(s) URLs whose host is one of our known document-storage hosts. */
    boolean isAllowedHost(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return false;
            }
            String host = uri.getHost();
            return host != null && allowedHosts().contains(host.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    private Set<String> allowedHosts() {
        Set<String> hosts = new HashSet<>();
        addHost(hosts, gatewayPublicUrl);
        addHost(hosts, s3PublicBaseUrl);
        return hosts;
    }

    private void addHost(Set<String> hosts, String url) {
        try {
            String host = URI.create(url).getHost();
            if (host != null) hosts.add(host.toLowerCase());
        } catch (Exception ignored) {
            // malformed config value — simply doesn't contribute to the allowlist
        }
    }
}
