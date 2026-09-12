package com.project.distributed_codegen.common_lib.security;

import com.project.distributed_codegen.common_lib.error.GlobalExceptionHandler;
import feign.RequestInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SharedSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "jwt.secret-key=test-only-jwt-key-with-at-least-32-bytes",
                    "internal.service-key=test-only-service-key");

    @Test
    void discoversSharedConfigurationWithoutScanningTheLibraryPackage() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AuthUtil.class)
                    .hasSingleBean(JwtAuthFilter.class)
                    .hasSingleBean(InternalServiceAuthFilter.class)
                    .hasSingleBean(RequestInterceptor.class)
                    .hasSingleBean(GlobalExceptionHandler.class);
        });
    }

    @Test
    void internalEndpointRequiresBothServiceCredentialsAndUserIdentity() {
        runner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            String token = context.getBean(AuthUtil.class).generateAccessToken(
                    new JwtUserPrincipal(42L, "Test", "test@example.test", null, List.of()));

            mvc.perform(get("/account/internal/v1/identity").contextPath("/account")
                            .servletPath("/internal/v1/identity").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/account/internal/v1/identity").contextPath("/account")
                            .servletPath("/internal/v1/identity")
                            .header(InternalServiceAuthFilter.INTERNAL_SERVICE_HEADER, "test-only-service-key"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/account/internal/v1/identity").contextPath("/account")
                            .servletPath("/internal/v1/identity").header("Authorization", "Bearer " + token)
                            .header(InternalServiceAuthFilter.INTERNAL_SERVICE_HEADER, "test-only-service-key"))
                    .andExpect(status().isOk()).andExpect(content().string("42"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        IdentityController identityController(AuthUtil authUtil) {
            return new IdentityController(authUtil);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return username -> { throw new UsernameNotFoundException("Test user not found"); };
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwt,
                                                 InternalServiceAuthFilter internal) {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .addFilterBefore(internal, UsernamePasswordAuthenticationFilter.class)
                    .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                    .exceptionHandling(errors -> errors.authenticationEntryPoint(
                            (request, response, exception) -> response.setStatus(401)))
                    .build();
        }
    }

    @RestController
    static class IdentityController {
        private final AuthUtil authUtil;

        IdentityController(AuthUtil authUtil) {
            this.authUtil = authUtil;
        }

        @GetMapping("/internal/v1/identity")
        Long identity() {
            return authUtil.getCurrentUserId();
        }
    }
}
