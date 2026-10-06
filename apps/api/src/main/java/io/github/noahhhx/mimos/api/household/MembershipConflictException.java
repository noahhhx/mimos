package io.github.noahhhx.mimos.api.household;

/**
 * A join or leave that contradicts the caller's membership: joining the
 * household they are in, or leaving a household of one (ADR-0019); mapped
 * to 409 by the API.
 */
public class MembershipConflictException extends RuntimeException {

    public MembershipConflictException(String message) {
        super(message);
    }
}
