package com.project.distributed_codegen.common_lib.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SharedSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthUtil authUtil() {
        return new AuthUtil();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthFilter jwtAuthFilter(AuthUtil authUtil,
                                       @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        return new JwtAuthFilter(authUtil, handlerExceptionResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public InternalServiceAuthFilter internalServiceAuthFilter(
            @Value("${internal.service-key:}") String serviceKey) {
        return new InternalServiceAuthFilter(serviceKey);
    }

    // These filters belong to Spring Security, not the container's separate filter chain.
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<InternalServiceAuthFilter> internalServiceAuthFilterRegistration(
            InternalServiceAuthFilter filter) {
        FilterRegistrationBean<InternalServiceAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public RequestInterceptor requestInterceptor(
            @Value("${internal.service-key:}") String serviceKey) {
        return requestTemplate -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (!requestTemplate.headers().containsKey("Authorization")
                    && authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof JwtUserPrincipal
                    && authentication.getCredentials() instanceof String token && !token.isBlank()) {
                requestTemplate.header("Authorization", "Bearer " + token);
            }
            if (!serviceKey.isBlank()) {
                requestTemplate.header(InternalServiceAuthFilter.INTERNAL_SERVICE_HEADER, serviceKey);
            }
        };
    }
}
