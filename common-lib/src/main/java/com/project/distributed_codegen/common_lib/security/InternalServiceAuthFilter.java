package com.project.distributed_codegen.common_lib.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class InternalServiceAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service-Key";

    private final byte[] expectedServiceKey;

    public InternalServiceAuthFilter(@Value("${internal.service-key:}") String serviceKey) {
        this.expectedServiceKey = serviceKey == null
                ? new byte[0]
                : serviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.equals("/internal") || path.startsWith("/internal/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        byte[] suppliedServiceKey = valueOrEmpty(request.getHeader(INTERNAL_SERVICE_HEADER))
                .getBytes(StandardCharsets.UTF_8);

        if (expectedServiceKey.length == 0
                || !MessageDigest.isEqual(expectedServiceKey, suppliedServiceKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Invalid internal service credentials\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
