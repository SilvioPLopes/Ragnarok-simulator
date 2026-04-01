package com.ragnarok.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock JwtUtil jwtUtil;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;
    @Mock PrintWriter writer;

    JwtFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtFilter(jwtUtil);
    }

    @Test
    void publicRoute_noToken_passes() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/accounts/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(401);
    }

    @Test
    void validToken_setsAccountId() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/players/1");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtUtil.isValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractAccountId("valid-token")).thenReturn(42L);

        filter.doFilterInternal(request, response, chain);

        verify(request).setAttribute("accountId", 42L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingToken_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/players/1");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void invalidToken_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/players/1");
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtUtil.isValid("bad-token")).thenReturn(false);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
