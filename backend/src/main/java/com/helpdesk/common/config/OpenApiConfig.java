package com.helpdesk.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI helpdeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Omnichannel AI Helpdesk Platform API")
                        .version("1.0.0")
                        .description("REST API Contract (UC00-UC08) với JWT RBAC, Optimistic Locking, Idempotency-Key và chuẩn lỗi RFC 7807 (ProblemDetail)."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
