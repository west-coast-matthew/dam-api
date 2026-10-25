package com.twofourseven.dam.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Generates static OpenAPI documentation from the controllers: {@code openapi.json} and a
 * self-contained {@code index.html}. No server is started and no port is opened: the application
 * context runs with a mock web environment and the spec is read through MockMvc. The database is
 * not needed (the app starts without it).
 *
 * <p>Not part of {@code mvn test}. Run it with {@code bin/generate-api-docs} or
 * {@code mvn -B test -Papi-docs}. Output goes to {@code target/api-docs} unless
 * {@code -Dapi-docs.output-dir=...} is given.
 */
@org.junit.jupiter.api.Tag("api-docs")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        // Placeholders so the context starts; no connection is made
        "dam.db.username=api-docs",
        "dam.db.password=api-docs",
        "dam.storage.root-dir=${java.io.tmpdir}/dam-api-docs",
        "springdoc.api-docs.enabled=true",
        "springdoc.writer-with-default-pretty-printer=true",
        "springdoc.writer-with-order-by-keys=true",
        "springdoc.default-produces-media-type=application/json"
})
class ApiDocsGenerator {

    static final String TITLE = "Digital Asset Management API";

    @Autowired
    private WebApplicationContext context;

    @org.junit.jupiter.api.Test
    void generate() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Path outputDir = Path.of(System.getProperty("api-docs.output-dir", "target/api-docs"));
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("openapi.json"), json);
        Files.writeString(outputDir.resolve("index.html"), ApiDocsHtml.render(TITLE, json));
        System.out.println("API docs written to " + outputDir.toAbsolutePath().resolve("index.html"));
    }

    @TestConfiguration
    static class DocsConfig {

        @Bean
        OpenAPI damOpenApi() {
            return new OpenAPI()
                    .info(new Info()
                            .title(TITLE)
                            .version(System.getProperty("api-docs.version", "dev"))
                            .description("REST API for storing, organizing and retrieving digital assets "
                                    + "(images, video and PDF). Errors are returned as ErrorResponseVO."))
                    .servers(List.of(new Server().url("http://localhost:8080").description("Local")));
        }

        @Bean
        OpenApiCustomizer conventionalResponses() {
            return new ConventionalResponses();
        }

        @Bean
        OpenApiCustomizer readableOperations() {
            return new ReadableOperations();
        }
    }
}
