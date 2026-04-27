package com.feedback.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOriginsValue;

    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatternsValue;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOrigins = splitCsv(allowedOriginsValue);
        if (allowedOrigins.contains("*")) {
            config.setAllowCredentials(false);
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowCredentials(true);
            config.setAllowedOrigins(allowedOrigins);
        }

        List<String> allowedOriginPatterns = splitCsv(allowedOriginPatternsValue);
        if (!allowedOriginPatterns.isEmpty()) {
            config.setAllowedOriginPatterns(allowedOriginPatterns);
        }

        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
