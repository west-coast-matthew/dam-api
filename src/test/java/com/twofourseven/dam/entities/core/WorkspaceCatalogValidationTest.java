package com.twofourseven.dam.entities.core;

import jakarta.persistence.Column;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the validation rules and column flags on Workspace and Catalog. No database involved. */
class WorkspaceCatalogValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void workspaceWithNameIsValid() {
        Workspace workspace = new Workspace();
        workspace.setName("Marketing");
        workspace.setDescription("Brand assets");

        assertThat(validator.validate(workspace)).isEmpty();
    }

    @Test
    void workspaceWithoutNameIsInvalid() {
        Workspace workspace = new Workspace();
        workspace.setName(" ");

        Set<ConstraintViolation<Workspace>> errors = validator.validate(workspace);

        assertThat(errors).extracting(v -> v.getPropertyPath().toString()).containsExactly("name");
    }

    @Test
    void catalogWithoutNameIsInvalid() {
        Catalog catalog = new Catalog();

        assertThat(validator.validate(catalog)).extracting(ConstraintViolation::getMessage)
                .containsExactly("Catalog name is required");
    }

    @Test
    void descriptionIsOptionalButLimited() {
        Catalog catalog = new Catalog();
        catalog.setName("Campaigns");
        assertThat(validator.validate(catalog)).isEmpty();

        catalog.setDescription("x".repeat(Catalog.DESCRIPTION_MAX_LENGTH + 1));
        assertThat(validator.validate(catalog)).hasSize(1);
    }

    @Test
    void nameColumnsAreRequiredAndUnique() throws NoSuchFieldException {
        Column workspaceName = Workspace.class.getDeclaredField("name").getAnnotation(Column.class);
        Column catalogName = Catalog.class.getDeclaredField("name").getAnnotation(Column.class);

        assertThat(workspaceName.nullable()).isFalse();
        assertThat(workspaceName.unique()).isTrue();
        assertThat(catalogName.nullable()).isFalse();
        assertThat(catalogName.unique()).isTrue();
    }

    @Test
    void newEntitiesGetIdsAndTimestamps() {
        Workspace workspace = new Workspace();
        Catalog catalog = new Catalog();
        workspace.addCatalog(catalog);

        assertThat(workspace.getId()).isNotNull();
        assertThat(workspace.getCreatedAt()).isNotNull();
        assertThat(catalog.getId()).isNotNull().isNotEqualTo(workspace.getId());
        assertThat(catalog.getWorkspace()).isSameAs(workspace);
        assertThat(workspace.getCatalogs()).containsExactly(catalog);
    }
}
