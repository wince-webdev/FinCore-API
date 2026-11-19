package com.wince.fincore.shared.infrastructure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Personnalisation du document OpenAPI servi dans Swagger UI.
 *
 * <p>Ce document est <strong>la référence du contrat</strong> entre les
 * équipes : titre, version du projet, contact, et surtout le schéma de
 * sécurité JWT (schéma « bearerAuth ») que Swagger UI utilisera dès la
 * phase 2 pour le bouton <em>Authorize</em>.</p>
 *
 * <p>Équivalent Symfony : la configuration {@code nelmio_api_doc}.</p>
 */
@Configuration
public class OpenApiConfig {

    /** Nom du schéma de sécurité porté par l'en-tête {@code Authorization}. */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI finCoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinCore API")
                        .version("1.0.0")
                        .description("""
                                API de cœur bancaire FinCore (UEMOA).

                                Tous les codes d'erreur métier sont documentés dans
                                `ErrorCode` et renvoyés au format RFC 9457
                                (`application/problem+json`) avec le champ `code`.
                                """)
                        .contact(new Contact()
                                .name("Équipe FinCore")
                                .email("dev@fincore.example"))
                        .license(new License()
                                .name("Propriétaire")
                                .url("https://fincore.example/licence")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT d'accès obtenu via POST /api/v1/auth/login")))
                // Pré-coche le schéma JWT dans Swagger UI (phase 2 l'activera).
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
