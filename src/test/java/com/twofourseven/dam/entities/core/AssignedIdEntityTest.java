package com.twofourseven.dam.entities.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssignedIdEntityTest {

    @Test
    void entityCreatedInJavaIsNewDespiteHavingAnId() {
        Catalog catalog = new Catalog();

        assertThat(catalog.getId()).isNotNull();
        assertThat(catalog.isNew()).isTrue();
    }

    @Test
    void entityIsNotNewOncePersistedOrLoaded() {
        Catalog catalog = new Catalog();

        catalog.markNotNew();

        assertThat(catalog.isNew()).isFalse();
    }
}
