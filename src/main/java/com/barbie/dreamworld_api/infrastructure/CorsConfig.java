package com.barbie.dreamworld_api.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    /**
     * Origem do frontend em produção (repo separado). Pode conter várias URLs
     * separadas por vírgula. Em desenvolvimento usa apenas o localhost.
     */
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080,http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                config.addAllowedOrigin(trimmed);
            }
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
        config.addAllowedHeader("Authorization");

        config.addExposedHeader("X-Rate-Limit-Remaining");
        config.addExposedHeader("X-Rate-Limit-Retry-After-Seconds");
        config.addExposedHeader("Retry-After");
        config.addExposedHeader("Location");

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
