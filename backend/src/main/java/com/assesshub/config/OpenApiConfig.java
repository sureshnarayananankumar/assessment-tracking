package com.assesshub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI assessHubOpenApi() {
        final String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("AssessHub API")
                        .version("1.0.0")
                        .description("Assignment and Assessment Management System. Authenticate with "
                                + "POST /api/auth/login and use the returned accessToken as a Bearer token.")
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .name(scheme)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
