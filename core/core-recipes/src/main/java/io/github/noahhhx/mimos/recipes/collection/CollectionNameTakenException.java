package io.github.noahhhx.mimos.recipes.collection;

/** Another collection of the household already has the name, ignoring case (409). */
public class CollectionNameTakenException extends RuntimeException {

    public CollectionNameTakenException(String name) {
        super("you already have a tab named \"" + name + "\"");
    }
}
