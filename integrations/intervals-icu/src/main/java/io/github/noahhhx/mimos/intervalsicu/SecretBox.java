package io.github.noahhhx.mimos.intervalsicu;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts a person's intervals.icu API key at rest (ADR-0022): AES-256-GCM
 * under the instance key {@code MIMOS_SECRET_KEY}, with a fresh 96-bit
 * nonce for every encryption. A value sealed under another key, or altered
 * since, never opens as garbage: {@link #open} throws
 * {@link UnreadableSecretException}.
 */
public final class SecretBox {

    static final int KEY_BYTES = 32;
    static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom random;

    SecretBox(String base64Key, SecureRandom random) {
        this.key = new SecretKeySpec(parseKey(base64Key), "AES");
        this.random = random;
    }

    /** What {@link #seal} returns; both parts are needed to open it. */
    public record Sealed(byte[] ciphertext, byte[] nonce) {}

    public Sealed seal(String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new Sealed(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), nonce);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is unavailable", e);
        }
    }

    public String open(Sealed sealed) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed.nonce()));
            return new String(cipher.doFinal(sealed.ciphertext()), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new UnreadableSecretException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is unavailable", e);
        }
    }

    /** The key's bytes, or an exception that names the setting but never echoes its value. */
    static byte[] parseKey(String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "MIMOS_SECRET_KEY is not base64; generate one with `openssl rand -base64 32`");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("MIMOS_SECRET_KEY must be " + KEY_BYTES + " bytes in base64, not "
                    + bytes.length + "; generate one with `openssl rand -base64 32`");
        }
        return bytes;
    }
}
