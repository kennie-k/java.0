package com.kenyarealestate.user.security;

import com.kenyarealestate.user.service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Directly exercises JwtAuthenticationFilter (bypassing the rest of the Spring Security chain)
 * to prove the blacklist and "tokens valid after" checks (added to close the JWT-revocation
 * gap) actually reject requests, on top of the pre-existing happy/invalid-token paths.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserDetailsService userDetailsService;
    @Mock private TokenBlacklistService tokenBlacklistService;

    private JwtAuthenticationFilter filter;

    private static final String TOKEN = "a-valid-looking-token";
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, tokenBlacklistService);
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithBearerToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void authenticatesRequest_withValidUnrevokedToken() throws Exception {
        when(jwtUtil.isValid(TOKEN)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtUtil.extractIssuedAt(TOKEN)).thenReturn(new Date());
        when(tokenBlacklistService.isIssuedBeforeInvalidation(eq(USER_ID), any())).thenReturn(false);
        when(jwtUtil.extractEmail(TOKEN)).thenReturn("jane@smartre.co.ke");
        UserDetails ud = User.withUsername("jane@smartre.co.ke").password("x").authorities(
                List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SELLER"))).build();
        when(userDetailsService.loadUserByUsername("jane@smartre.co.ke")).thenReturn(ud);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBearerToken(TOKEN), response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("jane@smartre.co.ke", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(200, response.getStatus()); // MockFilterChain doesn't set a status; default is 200 (untouched)
    }

    @Test
    void rejectsWithUnauthorized_whenTokenIsBlacklisted() throws Exception {
        when(jwtUtil.isValid(TOKEN)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(TOKEN)).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBearerToken(TOKEN), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    void rejectsWithUnauthorized_whenTokenIssuedBeforePasswordChange() throws Exception {
        when(jwtUtil.isValid(TOKEN)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
        Date oldIssuedAt = new Date(System.currentTimeMillis() - 100_000);
        when(jwtUtil.extractIssuedAt(TOKEN)).thenReturn(oldIssuedAt);
        when(tokenBlacklistService.isIssuedBeforeInvalidation(USER_ID, oldIssuedAt)).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBearerToken(TOKEN), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    void doesNotAuthenticate_whenTokenIsInvalid() throws Exception {
        when(jwtUtil.isValid(TOKEN)).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBearerToken(TOKEN), response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tokenBlacklistService);
        // Falls through to the chain so permitAll endpoints keep working unauthenticated;
        // Spring Security's authorization rules handle rejecting protected endpoints.
        assertEquals(200, response.getStatus());
    }

    @Test
    void doesNotAuthenticate_whenNoTokenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtUtil, tokenBlacklistService, userDetailsService);
    }

    @Test
    void clearsContext_whenUserFromTokenNoLongerExists() throws Exception {
        when(jwtUtil.isValid(TOKEN)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtUtil.extractIssuedAt(TOKEN)).thenReturn(new Date());
        when(tokenBlacklistService.isIssuedBeforeInvalidation(eq(USER_ID), any())).thenReturn(false);
        when(jwtUtil.extractEmail(TOKEN)).thenReturn("ghost@smartre.co.ke");
        when(userDetailsService.loadUserByUsername("ghost@smartre.co.ke"))
                .thenThrow(new UsernameNotFoundException("gone"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBearerToken(TOKEN), response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
