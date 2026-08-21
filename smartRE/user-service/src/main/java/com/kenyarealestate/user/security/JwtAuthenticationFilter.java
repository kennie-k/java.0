package com.kenyarealestate.user.security;

import com.kenyarealestate.user.service.TokenBlacklistService;
import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsService uds;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService uds, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.uds = uds;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String t = extract(req);
        if (StringUtils.hasText(t) && jwtUtil.isValid(t)) {

            if (tokenBlacklistService.isBlacklisted(t)) {
                SecurityContextHolder.clearContext();
                respondUnauthorized(res, "This session has been logged out. Please log in again.");
                return;
            }

            UUID userId = safeExtractUserId(t);
            if (tokenBlacklistService.isIssuedBeforeInvalidation(userId, jwtUtil.extractIssuedAt(t))) {
                SecurityContextHolder.clearContext();
                respondUnauthorized(res, "This session is no longer valid. Please log in again.");
                return;
            }

            try {
                var ud = uds.loadUserByUsername(jwtUtil.extractEmail(t));
                var auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (UsernameNotFoundException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }

    private UUID safeExtractUserId(String t) {
        try {
            return jwtUtil.extractUserId(t);
        } catch (Exception e) {
            return null;
        }
    }

    private void respondUnauthorized(HttpServletResponse res, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }

    private String extract(HttpServletRequest r) {
        if (r.getCookies() != null) {
            for (Cookie c : r.getCookies()) {
                if ("sre_token".equals(c.getName()) && StringUtils.hasText(c.getValue())) return c.getValue();
            }
        }
        String h = r.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
