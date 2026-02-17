package com.yem.hlm.backend.common.openapi;

import com.yem.hlm.backend.common.error.ErrorResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CRM-HLM API")
                        .version("1.0")
                        .description("Multi-tenant CRM for HLM. Every request is scoped to a single tenant via JWT `tid` claim."))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token from POST /auth/login. Contains `tid` (tenant ID) and `sub` (user ID) claims.")));
    }

    @Bean
    public OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            Schema<?> errorSchema = new Schema<ErrorResponse>()
                    .$ref("#/components/schemas/ErrorResponse");

            // Register ErrorResponse schema reference so Swagger UI shows it
            openApi.getComponents()
                    .addSchemas("ErrorResponse", new Schema<ErrorResponse>()
                            .type("object")
                            .description("Standard error envelope for all API errors. See ErrorCode enum for stable codes.")
                            .addProperty("timestamp", new Schema<String>().type("string"))
                            .addProperty("status", new Schema<Integer>().type("integer"))
                            .addProperty("error", new Schema<String>().type("string"))
                            .addProperty("code", new Schema<String>().type("string").description("Stable ErrorCode enum value"))
                            .addProperty("message", new Schema<String>().type("string"))
                            .addProperty("path", new Schema<String>().type("string"))
                            .addProperty("correlationId", new Schema<String>().type("string").nullable(true))
                            .addProperty("fieldErrors", new Schema<>().type("array").nullable(true)));

            // Add global 401/403 responses to all operations
            Content errorContent = new Content()
                    .addMediaType("application/json", new MediaType().schema(errorSchema));

            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        operation.getResponses()
                                .addApiResponse("401", new ApiResponse()
                                        .description("Unauthorized — missing or invalid JWT")
                                        .content(errorContent));
                        operation.getResponses()
                                .addApiResponse("403", new ApiResponse()
                                        .description("Forbidden — insufficient role")
                                        .content(errorContent));
                    }));
        };
    }
}
