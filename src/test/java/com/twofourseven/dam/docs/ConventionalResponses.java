package com.twofourseven.dam.docs;

import com.twofourseven.dam.vo.ErrorResponseVO;
import com.twofourseven.dam.vo.HeartbeatVO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.Map;
import java.util.TreeMap;

/**
 * Adds the responses springdoc can't see, following the project's conventions (CLAUDE.md):
 * <ul>
 *   <li>POST to a collection creates: 201 with a Location header.</li>
 *   <li>DELETE returns 204 with no body.</li>
 *   <li>Errors come from GlobalExceptionHandler as {@link ErrorResponseVO}: 400 for bad input,
 *       404 for an unknown {id}, 409 for conflicts on writes, 413 for oversized uploads,
 *       503 when the database is down and 500 otherwise.</li>
 *   <li>/heartbeat returns 503 with a {@link HeartbeatVO} when a check is DOWN.</li>
 * </ul>
 */
class ConventionalResponses implements OpenApiCustomizer {

    static final String ERROR_SCHEMA = "#/components/schemas/" + ErrorResponseVO.class.getSimpleName();
    private static final String JSON = "application/json";

    @Override
    public void customise(OpenAPI openApi) {
        registerSchema(openApi, ErrorResponseVO.class);

        openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
            if (path.equals("/heartbeat")) {
                registerSchema(openApi, HeartbeatVO.class);
                op.getResponses().addApiResponse("200", response("All checks are UP",
                        "#/components/schemas/" + HeartbeatVO.class.getSimpleName()));
                op.getResponses().addApiResponse("503", response("At least one check is DOWN",
                        "#/components/schemas/" + HeartbeatVO.class.getSimpleName()));
                return;
            }
            apply(path, method, op);
        }));
    }

    static void apply(String path, PathItem.HttpMethod method, Operation op) {
        ApiResponses responses = op.getResponses();
        boolean hasId = path.contains("{id}");
        boolean multipart = op.getRequestBody() != null && op.getRequestBody().getContent() != null
                && op.getRequestBody().getContent().containsKey("multipart/form-data");

        if (method == PathItem.HttpMethod.POST && !hasId) {
            ApiResponse ok = responses.remove("200");
            ApiResponse created = (ok != null ? ok : new ApiResponse()).description("Created")
                    .addHeaderObject("Location", new Header().description("URL of the new resource").schema(new StringSchema()));
            responses.addApiResponse("201", created);
        }
        if (method == PathItem.HttpMethod.DELETE) {
            responses.remove("200");
            responses.addApiResponse("204", new ApiResponse().description("Deleted"));
        }
        if (responses.containsKey("200")) {
            responses.get("200").description("OK");
        }

        boolean write = method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT;
        if (write || hasId || (op.getParameters() != null && !op.getParameters().isEmpty())) {
            responses.addApiResponse("400", error("Invalid request: validation failed, malformed body or bad parameter"));
        }
        if (hasId) {
            responses.addApiResponse("404", error("Not found"));
        }
        if (write || method == PathItem.HttpMethod.DELETE) {
            responses.addApiResponse("409", error("Conflicts with existing data (e.g. duplicate name, or still has children)"));
        }
        if (multipart) {
            responses.addApiResponse("413", error("Uploaded file is too large"));
        }
        responses.addApiResponse("500", error("Unexpected error (details are logged, not returned)"));
        responses.addApiResponse("503", error("Database is unavailable"));

        // Keep codes in numeric order in the output
        Map<String, ApiResponse> sorted = new TreeMap<>(responses);
        responses.clear();
        responses.putAll(sorted);
    }

    private static ApiResponse error(String description) {
        return response(description, ERROR_SCHEMA);
    }

    private static ApiResponse response(String description, String schemaRef) {
        return new ApiResponse().description(description)
                .content(new Content().addMediaType(JSON, new MediaType().schema(new Schema<>().$ref(schemaRef))));
    }

    private static void registerSchema(OpenAPI openApi, Class<?> type) {
        ModelConverters.getInstance().readAll(type).forEach(openApi.getComponents()::addSchemas);
    }
}
