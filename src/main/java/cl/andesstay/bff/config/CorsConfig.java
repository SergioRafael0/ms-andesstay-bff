package cl.andesstay.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS acotado a los orígenes permitidos (variable de entorno CORS_ALLOWED_ORIGINS,
 * separados por coma). Se evita la combinación "origen * + credenciales" de EP1.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${andesstay.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(allowedOrigins.split(","))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
