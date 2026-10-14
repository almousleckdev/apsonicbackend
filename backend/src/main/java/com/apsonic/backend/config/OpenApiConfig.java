package com.apsonic.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Apsonic CMS Backend API",
                version = "v1.0",
                description = "Enterprise Modular Monolith CMS REST API documentation for Apsonic Backend",
                contact = @Contact(name = "Apsonic Development Team", email = "dev@apsonic.com"),
                license = @License(name = "Proprietary", url = "https://apsonic.com")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Environment"),
                @Server(url = "https://api.apsonic.com", description = "Production Environment")
        },
        security = @SecurityRequirement(name = "BearerAuth")
)
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter JWT Bearer Token to authorize requests"
)
public class OpenApiConfig {
}
