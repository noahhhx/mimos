package io.github.noahhhx.mimos.intervalsicu;

/**
 * A sealed value that does not open under the current {@code MIMOS_SECRET_KEY}
 * for its owner: the operator changed the key since it was sealed, or the
 * value was altered or moved to another row.
 */
public class UnreadableSecretException extends RuntimeException {

    UnreadableSecretException(Throwable cause) {
        super("The stored secret does not open under the current MIMOS_SECRET_KEY", cause);
    }
}
