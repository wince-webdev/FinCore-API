package com.wince.fincore.identity.infrastructure;

import com.wince.fincore.identity.application.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Les deux beans dont Spring Security a besoin pour faire fonctionner le
 * <em>resource server</em> JWT — publiés depuis {@code identity} plutôt que
 * depuis {@code shared}.
 *
 * <p><strong>Pourquoi ici</strong> : la chaîne de sécurité vit dans
 * {@code shared} pour rester indépendante du métier, mais elle a besoin
 * d'un {@code JwtDecoder} et d'un convertisseur d'autorisations dont le
 * contenu dépend du secret et du contrat de jetons d'identité. On publie
 * donc les beans du côté {@code identity} : Spring Security les découvre
 * et les branche tout seul, sans que {@code shared} n'importe jamais une
 * classe d'{@code identity} (le sens des dépendances est préservé).</p>
 *
 * <p><strong>Une seule clé</strong> : le décodeur est fabriqué par
 * {@link JwtService} à partir de l'exacte même clé que l'encodeur — deux
 * lectures indépendantes du même property serait une source de divergence
 * silencieuse (jetons signés d'un côté, rejetés de l'autre).</p>
 */
@Configuration
public class IdentitySecurityConfig {

    /**
     * Vérificateur des access tokens (signature HS256 + dates {@code iat/exp}).
     * Spring Security le cherche par type {@code JwtDecoder} : aucune
     * configuration supplémentaire n'est nécessaire dans {@code SecurityConfig}.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.jwtDecoder();
    }

    /**
     * Traduit le claim {@code role} du JWT en autorité Spring
     * {@code ROLE_CUSTOMER}… — c'est ce qui rend possibles les
     * {@code hasRole("ADMIN")} de la matrice des droits (§5).
     *
     * <p>Le bean est de type concret {@code JwtAuthenticationConverter} :
     * c'est exactement la classe que {@code OAuth2ResourceServerConfigurer}
     * cherche dans le contexte pour remplacer son convertisseur par défaut
     * (qui ne connaîtrait que le claim {@code authorities}).</p>
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role"); // claim émis par JwtService
        authorities.setAuthorityPrefix("ROLE_"); // → ROLE_ADMIN, compatible hasRole(...)
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
