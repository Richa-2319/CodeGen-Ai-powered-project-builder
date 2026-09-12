package com.project.distributed_codegen.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private List<String> publicRoutes = new ArrayList<>();
    private List<String> blockedRoutes = List.of("/internal/**", "/**/internal/**");

    public List<String> getPublicRoutes() {
        return publicRoutes;
    }

    public void setPublicRoutes(List<String> publicRoutes) {
        this.publicRoutes = publicRoutes == null ? new ArrayList<>() : publicRoutes;
    }

    public List<String> getBlockedRoutes() {
        return blockedRoutes;
    }

    public void setBlockedRoutes(List<String> blockedRoutes) {
        this.blockedRoutes = blockedRoutes == null ? new ArrayList<>() : blockedRoutes;
    }

}
