package com.twofourseven.dam.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConventionalResponsesTest {

    private static Operation op() {
        return new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("x")));
    }

    @Test
    void createIs201WithLocationAndErrors() {
        Operation post = op().requestBody(new RequestBody());

        ConventionalResponses.apply("/workspaces", PathItem.HttpMethod.POST, post);

        assertThat(post.getResponses()).containsOnlyKeys("201", "400", "409", "500", "503");
        assertThat(post.getResponses().get("201").getHeaders()).containsKey("Location");
        assertThat(post.getResponses().get("409").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo(ConventionalResponses.ERROR_SCHEMA);
    }

    @Test
    void bulkCreateIs201WithoutLocation() {
        Operation post = op().requestBody(new RequestBody());

        ConventionalResponses.apply("/assets/bulk", PathItem.HttpMethod.POST, post);

        assertThat(post.getResponses()).containsKey("201");
        assertThat(post.getResponses().get("201").getHeaders()).isNull();
    }

    @Test
    void deleteIs204() {
        Operation delete = op();

        ConventionalResponses.apply("/workspaces/{id}", PathItem.HttpMethod.DELETE, delete);

        assertThat(delete.getResponses()).containsOnlyKeys("204", "400", "404", "409", "500", "503");
    }

    @Test
    void getByIdHas404ButNoConflict() {
        Operation get = op();

        ConventionalResponses.apply("/assets/{id}", PathItem.HttpMethod.GET, get);

        assertThat(get.getResponses()).containsOnlyKeys("200", "400", "404", "500", "503");
    }

    @Test
    void listWithoutParametersHasNo400() {
        Operation list = op();

        ConventionalResponses.apply("/workspaces", PathItem.HttpMethod.GET, list);

        assertThat(list.getResponses()).containsOnlyKeys("200", "500", "503");
    }

    @Test
    void listWithParameterCanBe400() {
        Operation list = op().parameters(List.of(new Parameter().name("catalogId")));

        ConventionalResponses.apply("/categories", PathItem.HttpMethod.GET, list);

        assertThat(list.getResponses()).containsKey("400");
    }

    @Test
    void multipartUploadCanBe413() {
        Operation put = op().requestBody(new RequestBody().content(
                new Content().addMediaType("multipart/form-data", new MediaType())));

        ConventionalResponses.apply("/assets/{id}/file", PathItem.HttpMethod.PUT, put);

        assertThat(put.getResponses()).containsKeys("200", "404", "413");
    }

    @Test
    void heartbeatGets503WithHeartbeatBodyAndErrorSchemaIsRegistered() {
        OpenAPI api = new OpenAPI().components(new Components())
                .paths(new Paths().addPathItem("/heartbeat", new PathItem().get(op())));

        new ConventionalResponses().customise(api);

        Operation get = api.getPaths().get("/heartbeat").getGet();
        assertThat(get.getResponses()).containsOnlyKeys("200", "503");
        assertThat(get.getResponses().get("503").getContent().get("application/json").getSchema().get$ref())
                .endsWith("/HeartbeatVO");
        assertThat(api.getComponents().getSchemas()).containsKeys("ErrorResponseVO", "HeartbeatVO");
    }
}
