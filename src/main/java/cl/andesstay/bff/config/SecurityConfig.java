package cl.andesstay.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Seguridad del BFF AndesStay.
 *
 * - Valida el JWT de Entra ID: firma (JWKS), issuer, vigencia y AUDIENCE.
 * - Convierte el claim "roles" en autoridades ROLE_*.
 * - Aplica autorización por rol según la matriz de permisos de AndesStay:
 *     · Ver/crear reservas: Huesped, Recepcionista, Admin
 *     · Cambiar estados de reserva: Recepcionista, Admin
 *     · Consultar catálogo: cualquier rol autenticado
 *     · Gestionar catálogo: Recepcionista, Admin
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${andesstay.security.audience:api://andesstay-api}")
    private String audience;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/error").permitAll()
                // Reservas: lectura y creación para cualquier rol autenticado
                .requestMatchers(HttpMethod.GET, "/api/reservations/**")
                    .hasAnyRole("Huesped", "Recepcionista", "Admin")
                .requestMatchers(HttpMethod.POST, "/api/reservations/**")
                    .hasAnyRole("Huesped", "Recepcionista", "Admin")
                // Reservas: cambio de estado / edición solo para Recepcionista y Admin
                .requestMatchers("/api/reservations/**")
                    .hasAnyRole("Recepcionista", "Admin")
                // Catálogo: consulta para cualquier rol autenticado
                .requestMatchers(HttpMethod.GET, "/api/catalog/**")
                    .hasAnyRole("Huesped", "Recepcionista", "Admin")
                // Catálogo: alta/baja/cambio solo para Recepcionista y Admin
                .requestMatchers("/api/catalog/**")
                    .hasAnyRole("Recepcionista", "Admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            );
        return http.build();
    }

    /**
     * Decoder JWT que añade validación de AUDIENCE además de las validaciones
     * por defecto (firma, issuer, expiración). Sin esto, cualquier token firmado
     * por el tenant sería aceptado aunque no estuviera emitido para esta API.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidators, audienceValidator));
        return decoder;
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                authorities.addAll(roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList()));
            }
            return authorities;
        });
        return converter;
    }

    /** Rechaza tokens cuyo claim "aud" no incluya el identificador de esta API. */
    static class AudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final String expectedAudience;

        AudienceValidator(String expectedAudience) {
            this.expectedAudience = expectedAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> aud = jwt.getAudience();
            if (aud != null && aud.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "El audience del token no corresponde a esta API (esperado: " + expectedAudience + ")",
                    null);
            return OAuth2TokenValidatorResult.failure(error);
        }
    }
}
