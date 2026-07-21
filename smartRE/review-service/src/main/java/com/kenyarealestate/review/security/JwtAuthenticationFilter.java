package com.kenyarealestate.review.security;

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
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Value("${gateway.signing-secret}")
    private String signingSecret;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String email     = request.getHeader("X-Auth-Email");
        String role      = request.getHeader("X-Auth-Role");
        String userId    = request.getHeader("X-Auth-UserId");
        String signature = request.getHeader("X-Auth-Signature");

        boolean headersTrusted = StringUtils.hasText(email)
                && StringUtils.hasText(signature)
                && signature.equals(sign(email, role, userId));

        if (!headersTrusted) {
            email = null; role = null; userId = null;
            String token = extractToken(request);
            if (StringUtils.hasText(token) && jwtUtil.isValid(token)) {
                email  = jwtUtil.extractEmail(token);
                role   = jwtUtil.extractRole(token);
                UUID uid = jwtUtil.extractUserId(token);
                if (uid != null) userId = uid.toString();
            }
        }

        if (StringUtils.hasText(email) && StringUtils.hasText(role)) {
            var auth = new UsernamePasswordAuthenticationToken(
                    email, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            if (StringUtils.hasText(userId)) {
                request.setAttribute("authenticatedUserId", UUID.fromString(userId));
            }
        }

        chain.doFilter(request, response);
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

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("sre_token".equals(c.getName()) && StringUtils.hasText(c.getValue())) return c.getValue();
            }
        }
        String h = request.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
