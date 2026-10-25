package com.twofourseven.dam.docs;

import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadableOperationsTest {

    @Test
    void crudOperationsGetResourceTitles() {
        Operation list = new Operation().operationId("list_4").tags(List.of("category-controller"));

        ReadableOperations.apply(list);

        assertThat(list.getTags()).containsExactly("Category");
        assertThat(list.getSummary()).isEqualTo("List categories");
        assertThat(list.getOperationId()).isEqualTo("listCategory");
    }

    @Test
    void otherOperationsUseTheMethodName() {
        Operation op = new Operation().operationId("replaceFile").tags(List.of("asset-controller"));

        ReadableOperations.apply(op);

        assertThat(op.getSummary()).isEqualTo("Replace file");
        assertThat(op.getOperationId()).isEqualTo("replaceFileAsset");
    }

    @Test
    void summaries() {
        assertThat(ReadableOperations.summary("get", "Lightbox")).isEqualTo("Get lightbox");
        assertThat(ReadableOperations.summary("list", "Lightbox")).isEqualTo("List lightboxes");
        assertThat(ReadableOperations.summary("create", "Workspace")).isEqualTo("Create workspace");
        assertThat(ReadableOperations.summary("heartbeat", "Heartbeat")).isEqualTo("Heartbeat");
    }

    @Test
    void tagNames() {
        assertThat(ReadableOperations.tagName("workspace-controller")).isEqualTo("Workspace");
        assertThat(ReadableOperations.tagName("asset-version-controller")).isEqualTo("Asset Version");
        assertThat(ReadableOperations.tagName("Already Nice")).isEqualTo("Already Nice");
    }

    @Test
    void plurals() {
        assertThat(ReadableOperations.plural("category")).isEqualTo("categories");
        assertThat(ReadableOperations.plural("key")).isEqualTo("keys");
        assertThat(ReadableOperations.plural("lightbox")).isEqualTo("lightboxes");
        assertThat(ReadableOperations.plural("asset")).isEqualTo("assets");
    }

    @Test
    void idIsNotRepeatedWhenMethodIsTheResource() {
        Operation op = new Operation().operationId("heartbeat").tags(List.of("heartbeat-controller"));

        ReadableOperations.apply(op);

        assertThat(op.getOperationId()).isEqualTo("heartbeat");
    }

    @Test
    void existingSummaryIsKept() {
        Operation op = new Operation().operationId("get").summary("Custom").tags(List.of("asset-controller"));

        ReadableOperations.apply(op);

        assertThat(op.getSummary()).isEqualTo("Custom");
    }
}
