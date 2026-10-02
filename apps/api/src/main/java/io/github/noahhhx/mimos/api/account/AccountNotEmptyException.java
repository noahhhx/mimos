package io.github.noahhhx.mimos.api.account;

/** Import is a restore into an empty account (ADR-0011); mapped to 409 by the API. */
public class AccountNotEmptyException extends RuntimeException {

    public AccountNotEmptyException(String message) {
        super(message);
    }
}
