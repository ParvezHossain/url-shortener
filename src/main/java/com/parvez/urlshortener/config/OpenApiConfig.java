package com.parvez.urlshortener.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Defines API metadata and reusable problem responses for the public endpoints. */
@Configuration
public class OpenApiConfig {

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
                .addResponses("BadRequest", problemResponse("Invalid URL, alias, expiry, validation failure, or malformed JSON"))
                .addResponses("Conflict", problemResponse("Custom alias already taken"))
                .addResponses("NotFound", problemResponse("Unknown or deleted short code"))
                .addResponses("Gone", problemResponse("Link has expired"))
                .addResponses("ServerError", problemResponse("Unexpected failure; no internal exception details exposed"));
        return new OpenAPI().info(new Info().title("URL Shortener API").version("v1")
                .description("Create, resolve, inspect, and delete short links."))
                .components(components);
    }

    private ApiResponse problemResponse(String description) {
        return new ApiResponse().description(description).content(new Content()
                .addMediaType("application/problem+json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))));
    }
}
