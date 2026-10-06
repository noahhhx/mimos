package io.github.noahhhx.mimos.api.household;

/** An invite that existed but expired or was used (ADR-0019); mapped to 410 by the API. */
public class InviteNoLongerValidException extends RuntimeException {

    public InviteNoLongerValidException(String message) {
        super(message);
    }
}
