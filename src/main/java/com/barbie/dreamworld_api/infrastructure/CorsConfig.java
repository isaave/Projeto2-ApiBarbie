package com.barbie.dreamworld_api.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    /**
     * Origens do frontend. Em produção preencher CORS_ALLOWED_ORIGINS no Render.
     * Aceita "*" (usa allowedOriginPatterns, compatível com allowCredentials)
     * ou uma lista de URLs separadas por vírgula.
     */
    @Value("${cors.allowed-origins:https://isaave.github.io}")
    private String allowedOrigins;

    // Precisa de correr ANTES do ApiKeyFilter (@Order(1)), senão as respostas
    // 401/403 são escritas pelo filtro de segurança sem headers CORS e o
    // browser reporta "No Access-Control-Allow-Origin header" em vez do erro real.
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        boolean algumaOrigem = false;

        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("*".equals(trimmed)) {
                // Com allowCredentials=true, o Spring exige allowedOriginPatterns
                // em vez de addAllowedOrigin("*")
                config.addAllowedOriginPattern("*");
            } else {
                config.addAllowedOrigin(trimmed);
            }
            algumaOrigem = true;
        }

        if (!algumaOrigem) {
            config.addAllowedOrigin("https://isaave.github.io");
        }

        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");

        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("Accept");
        config.addAllowedHeader("X-API-Key");
        config.addAllowedHeader("Idempotency-Key");

        config.addExposedHeader("X-Rate-Limit-Remaining");
        config.addExposedHeader("X-Rate-Limit-Retry-After-Seconds");
        config.addExposedHeader("Retry-After");
        config.addExposedHeader("Location");

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
