package com.project.distributed_codegen.common_lib.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalServiceAuthFilterTest {

    private final InternalServiceAuthFilter filter = new InternalServiceAuthFilter("service-key");
    private final FilterChain filterChain = mock(FilterChain.class);

    @Test
    void rejectsInternalRequestWithoutServiceKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/users/1");
        request.setServletPath("/internal/v1/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void rejectsInternalRootWithoutServiceKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal");
        request.setServletPath("/internal");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void acceptsInternalRequestWithServiceKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/users/1");
        request.setServletPath("/internal/v1/users/1");
        request.addHeader(InternalServiceAuthFilter.INTERNAL_SERVICE_HEADER, "service-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void leavesPublicRequestUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login");
        request.setServletPath("/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
