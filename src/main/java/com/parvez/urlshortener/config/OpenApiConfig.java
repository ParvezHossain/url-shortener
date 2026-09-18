package com.parvez.urlshortener.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Defines API metadata and reusable problem responses for the public endpoints. */
@Configuration
@OpenAPIDefinition(
        info = @io.swagger.v3.oas.annotations.info.Info(
                title = "URL Shortener API",
                version = "v2",
                description = OpenApiConfig.DESCRIPTION,
                contact = @io.swagger.v3.oas.annotations.info.Contact(
                        name = "Parvez Hossain",
                        email = "parvezhossain724@gmail.com"
                ),
                license = @io.swagger.v3.oas.annotations.info.License(
                        name = "Apache 2.0",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"
                )
        )
)
public class OpenApiConfig {

    static final String DESCRIPTION = "V2 management requires X-API-Key and is owner-scoped. "
                        + "V1 is deprecated from 2026-09-17 and accesses only unowned legacy links. "
                        + "APP_V1_SUNSET optionally sets deployment retirement (ISO-8601); after that v1 returns 410. "
                        + "No default sunset date is imposed. Public redirects remain available. "
                        + "Migration: provision an operator-issued key, then send X-API-Key to /api/v2/urls. "
                        + "Legacy ownership is never inferred; recreate links in v2 with a new code.";

    /** Describes the versioned API and its shared error response contract. */
    @Bean
    public OpenAPI urlShortenerOpenApi() {
        var problem = new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri").example("about:blank"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema().format("int32"))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("errors", new ArraySchema().items(new StringSchema())
                        .description("Field validation messages; present only for bean validation failures"));
        var components = new Components().addSchemas("ApiProblem", problem)
                .addSecuritySchemes("ApiKey", new io.swagger.v3.oas.models.security.SecurityScheme()
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
                        .in(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER).name("X-API-Key"))
                .addResponses("RateLimited", problemResponse("Request quota exhausted when rate limiting is enabled")
                        .addHeaderObject("Retry-After", new io.swagger.v3.oas.models.headers.Header()
                                .description("Seconds until retry").schema(new IntegerSchema()))
                        .addHeaderObject("RateLimit-Limit", new io.swagger.v3.oas.models.headers.Header()
                                .description("Requests per window").schema(new IntegerSchema()))
                        .addHeaderObject("RateLimit-Remaining", new io.swagger.v3.oas.models.headers.Header()
                                .description("Remaining requests").schema(new IntegerSchema()))
                        .addHeaderObject("RateLimit-Reset", new io.swagger.v3.oas.models.headers.Header()
                                .description("Seconds until reset").schema(new IntegerSchema())))
                .addResponses("QuotaUnavailable", problemResponse("Quota store unavailable; management fails closed")
                        .addHeaderObject("Retry-After", new io.swagger.v3.oas.models.headers.Header()
                                .description("Seconds until retry").schema(new IntegerSchema())))
                .addResponses("Unauthorized", problemResponse("Missing, invalid, or revoked API key"))
                .addResponses("Forbidden", problemResponse("Authenticated caller lacks permission"))
                .addResponses("BadRequest", problemResponse("Invalid URL, alias, expiry, validation failure, or malformed JSON"))
                .addResponses("Conflict", problemResponse("Custom alias already taken"))
                .addResponses("NotFound", problemResponse("Unknown or deleted short code"))
                .addResponses("Gone", problemResponse("Link has expired"))
                .addResponses("ServerError", problemResponse("Unexpected failure; no internal exception details exposed"));
        return new OpenAPI().info(new Info().title("URL Shortener API").version("v2")
                .description(DESCRIPTION))
                .addServersItem(new Server().url("/").description("Current origin"))
                .components(components);
    }

    private ApiResponse problemResponse(String description) {
        return new ApiResponse().description(description).content(new Content()
                .addMediaType("application/problem+json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))));
    }
}
