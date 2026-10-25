package com.twofourseven.dam.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Makes the generated names readable:
 * <ul>
 *   <li>tags: "workspace-controller" -> "Workspace"</li>
 *   <li>titles: controller method "list" under "Asset" -> "List assets", "replaceFile" -> "Replace file"</li>
 *   <li>operation ids: "list_4" -> "listAsset", so they're unique and stable</li>
 * </ul>
 */
class ReadableOperations implements OpenApiCustomizer {

    private static final Set<String> CRUD = Set.of("list", "get", "create", "update", "delete");

    @Override
    public void customise(OpenAPI openApi) {
        openApi.getPaths().values().forEach(path -> path.readOperations().forEach(ReadableOperations::apply));
        if (openApi.getTags() != null) {
            openApi.getTags().forEach(tag -> tag.setName(tagName(tag.getName())));
            openApi.getTags().sort((a, b) -> a.getName().compareTo(b.getName()));
        }
    }

    static void apply(Operation op) {
        List<String> tags = op.getTags() == null ? List.of() : op.getTags().stream().map(ReadableOperations::tagName).toList();
        op.setTags(tags.isEmpty() ? null : tags);
        String resource = tags.isEmpty() ? "" : tags.getFirst();
        // springdoc de-duplicates ids by adding "_N"
        String method = op.getOperationId() == null ? "" : op.getOperationId().replaceFirst("_\\d+$", "");
        if (method.isEmpty()) {
            return;
        }
        if (op.getSummary() == null) {
            op.setSummary(summary(method, resource));
        }
        String suffix = resource.replace(" ", "");
        op.setOperationId(method.equalsIgnoreCase(suffix) ? method : method + suffix);
    }

    static String summary(String method, String resource) {
        String words = humanize(method);
        if (!CRUD.contains(method) || resource.isEmpty()) {
            return words;
        }
        String noun = resource.toLowerCase(Locale.ROOT);
        return words + " " + (method.equals("list") ? plural(noun) : noun);
    }

    /** "workspace-controller" -> "Workspace", "asset-version-controller" -> "Asset Version" */
    static String tagName(String tag) {
        String base = tag.endsWith("-controller") ? tag.substring(0, tag.length() - "-controller".length()) : tag;
        return Arrays.stream(base.split("-"))
                .filter(w -> !w.isEmpty())
                .map(ReadableOperations::capitalize)
                .collect(Collectors.joining(" "));
    }

    /** "replaceFile" -> "Replace file" */
    static String humanize(String camel) {
        return capitalize(camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT));
    }

    static String plural(String noun) {
        if (noun.endsWith("y") && !noun.matches(".*[aeiou]y$")) {
            return noun.substring(0, noun.length() - 1) + "ies";
        }
        if (noun.endsWith("x") || noun.endsWith("s") || noun.endsWith("ch") || noun.endsWith("sh")) {
            return noun + "es";
        }
        return noun + "s";
    }

    private static String capitalize(String word) {
        return word.isEmpty() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }
}
