package com.project.distributed_codegen.common_lib.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceRequestInterceptorTest {
    private final RequestInterceptor interceptor = new SharedSecurityAutoConfiguration()
            .requestInterceptor("test-only-service-key");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsJwtAndServiceIdentityToFeign() {
        authenticate();
        RequestTemplate request = new RequestTemplate();

        interceptor.apply(request);

        assertThat(request.headers().get("Authorization")).containsExactly("Bearer test-only-request-token");
        assertThat(request.headers().get(InternalServiceAuthFilter.INTERNAL_SERVICE_HEADER))
                .containsExactly("test-only-service-key");
    }

    @Test
    void preservesExplicitIdentityCapturedForAnAsyncCall() {
        authenticate();
        RequestTemplate request = new RequestTemplate();
        request.header("Authorization", "Bearer test-only-captured-token");

        interceptor.apply(request);

        assertThat(request.headers().get("Authorization")).containsExactly("Bearer test-only-captured-token");
    }

    @Test
    void doesNotForwardAnonymousCredentialsAsAJwt() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test-only", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        RequestTemplate request = new RequestTemplate();

        interceptor.apply(request);

        assertThat(request.headers()).doesNotContainKey("Authorization");
    }

    private void authenticate() {
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "Test", "test@example.test", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "test-only-request-token", List.of()));
    }
}
