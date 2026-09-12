package com.project.distributed_codegen.common_lib.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {
    private final AuthUtil auth = mock(AuthUtil.class);
    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(auth, resolver);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void propagatesDownstreamFailureInsteadOfTurningItIntoAnEmptySuccess() {
        MockHttpServletRequest request = authenticatedRequest();
        when(auth.verifyAccessToken("test-only-token"))
                .thenReturn(new JwtUserPrincipal(1L, "Test", "test@example.test", null, List.of()));

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (incoming, outgoing) -> { throw new IllegalStateException("test downstream failure"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("test downstream failure");
        verifyNoInteractions(resolver);
    }

    @Test
    void rejectsAnEmptyBearerCredentialEvenWithoutAnExceptionHandler() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        request.addHeader("Authorization", "Bearer ");
        when(auth.verifyAccessToken("")).thenThrow(new IllegalArgumentException("test empty token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        request.addHeader("Authorization", "Bearer test-only-token");
        return request;
    }
}
