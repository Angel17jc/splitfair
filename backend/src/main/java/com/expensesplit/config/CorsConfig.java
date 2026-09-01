package com.expensesplit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * Origenes permitidos, separados por coma. Se configura por entorno
     * (CORS_ALLOWED_ORIGINS) porque el dominio del frontend cambia entre
     * desarrollo y produccion.
     */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // Sin esto el navegador NO deja que el frontend lea Retry-After.
        // Solo un punado de cabeceras son legibles por defecto en una
        // respuesta entre origenes distintos, y esta no esta entre ellas: el
        // cliente recibia el 429 pero no podia decir cuanto esperar, asi que
        // caia en un mensaje vago. Se expone solo esta, no una lista amplia:
        // cada cabecera expuesta es informacion que se entrega a cualquier
        // script de la pagina.
        config.setExposedHeaders(List.of("Retry-After"));

        // Imprescindible: el refresh token viaja en una cookie HttpOnly y una
        // peticion entre origenes distintos no la envia sin esto. En
        // desarrollo lo son (5173 contra 8080).
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
