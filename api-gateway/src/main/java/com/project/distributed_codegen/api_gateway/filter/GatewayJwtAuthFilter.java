package com.project.distributed_codegen.api_gateway.filter;


import tools.jackson.databind.ObjectMapper;
import com.project.distributed_codegen.api_gateway.config.SecurityProperties;
import com.project.distributed_codegen.api_gateway.error.ApiError;
import com.project.distributed_codegen.api_gateway.service.JwtGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayJwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayJwtAuthFilter.class);

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final SecurityProperties securityProperties;
    private final JwtGatewayService jwtGatewayService;

    private final ObjectMapper objectMapper;

    public GatewayJwtAuthFilter(
            SecurityProperties securityProperties,
            JwtGatewayService jwtGatewayService,
            ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.jwtGatewayService = jwtGatewayService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        boolean isBlocked = securityProperties.getBlockedRoutes().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
        if (isBlocked) {
            log.warn("Blocked external request to internal route: {}", path);
            return sendErrorResponse(exchange, HttpStatus.NOT_FOUND, "Route not found");
        }

        boolean isPublic = securityProperties.getPublicRoutes().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isPublic) {
            log.info("Public route, continue: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.error("Missing or invalid Authorization header for path: {}", path);
            return sendErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            jwtGatewayService.validateToken(token);
            log.info("JWT token valid for path: {}", path);
        } catch (Exception e) {
            log.error("JWT Validation failed at Gateway: {}", e.getMessage());
            return sendErrorResponse(exchange, HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        return chain.filter(exchange);
    }

    private Mono<Void> sendErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        ApiError apiError = new ApiError(status, message);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(apiError);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error serializing gateway error response", e);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
