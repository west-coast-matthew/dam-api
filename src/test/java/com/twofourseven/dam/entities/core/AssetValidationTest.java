package com.twofourseven.dam.entities.core;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** An asset must have at least one category. No database involved. */
class AssetValidationTest {

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
    void assetWithoutCategoryIsInvalid() {
        Set<ConstraintViolation<Asset>> violations = validator.validate(new Asset());

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("An asset must have at least one category");
    }

    @Test
    void assetWithDefaultCategoryIsValid() {
        Catalog catalog = new Catalog();
        Asset asset = new Asset();
        asset.getCategories().add(Category.createDefaultFor(catalog));

        assertThat(validator.validate(asset)).isEmpty();
    }
}
