package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.Workspace;

/** In-memory entities for service tests. */
final class Fixtures {

    private Fixtures() {
    }

    static Workspace workspace(String name) {
        Workspace w = new Workspace();
        w.setName(name);
        return w;
    }

    static Catalog catalog(String name) {
        Catalog c = new Catalog();
        c.setName(name);
        workspace("Workspace for " + name).addCatalog(c);
        return c;
    }

    static Category category(Catalog catalog, Category parent, String name) {
        Category c = new Category();
        c.setCatalog(catalog);
        c.setName(name);
        c.setSlug(Strings.slugify(name));
        if (parent != null) {
            c.setParent(parent);
            parent.getChildren().add(c);
        }
        return c;
    }
}
