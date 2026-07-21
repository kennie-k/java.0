package com.kenyarealestate.payment.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.*;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Value("${services.internal-secret}")
    private String internalSecret;

    @Value("${gateway.signing-secret}")
    private String signingSecret;

    public JwtAuthenticationFilter(JwtUtil j) { this.jwtUtil = j; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String secret = req.getHeader("X-Internal-Secret");
        if (StringUtils.hasText(secret) && secret.equals(internalSecret)) {
            String userId = req.getHeader("X-Auth-UserId");
            String role   = req.getHeader("X-Auth-Role");
            String email  = req.getHeader("X-Auth-Email");
            if (!StringUtils.hasText(email)) email = "internal@smartre.system";
            if (!StringUtils.hasText(role))  role  = "SYSTEM";
            var auth = new UsernamePasswordAuthenticationToken(
                    email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);
            if (StringUtils.hasText(userId)) {
                req.setAttribute("authenticatedUserId", UUID.fromString(userId));
            }
            chain.doFilter(req, res);
            return;
        }

        String email     = req.getHeader("X-Auth-Email");
        String role      = req.getHeader("X-Auth-Role");
        String userId    = req.getHeader("X-Auth-UserId");
        String signature = req.getHeader("X-Auth-Signature");

        boolean headersTrusted = StringUtils.hasText(email)
                && StringUtils.hasText(signature)
                && signature.equals(sign(email, role, userId));

        if (!headersTrusted) {
            email = null; role = null; userId = null;
            String t = extractToken(req);
            if (StringUtils.hasText(t) && jwtUtil.isValid(t)) {
                email  = jwtUtil.extractEmail(t);
                role   = jwtUtil.extractRole(t);
                UUID uid = jwtUtil.extractUserId(t);
                if (uid != null) userId = uid.toString();
            }
        }

        if (StringUtils.hasText(email) && StringUtils.hasText(role)) {
            var auth = new UsernamePasswordAuthenticationToken(
                    email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);
            if (StringUtils.hasText(userId)) {
                req.setAttribute("authenticatedUserId", UUID.fromString(userId));
            }
        }

        chain.doFilter(req, res);
    }

    private String sign(String email, String role, String userId) {
        try {
            String message = email + ":" + (role != null ? role : "") + ":" + (userId != null ? userId : "");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private String extractToken(HttpServletRequest r) {
        if (r.getCookies() != null) {
            for (Cookie c : r.getCookies()) {
                if ("sre_token".equals(c.getName()) && StringUtils.hasText(c.getValue())) return c.getValue();
            }
        }
        String h = r.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
