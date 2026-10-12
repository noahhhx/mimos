package io.github.noahhhx.mimos.intervalsicu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretBoxTests {

    static final String KEY = base64Key(1);
    private static final String OTHER_KEY = base64Key(2);
    private static final String API_KEY = "5u4m9xq0intervalskey";

    private final SecretBox box = new SecretBox(KEY, new SecureRandom());

    @Test
    void opensWhatItSealed() {
        SecretBox.Sealed sealed = box.seal(API_KEY);

        assertThat(box.open(sealed)).isEqualTo(API_KEY);
        assertThat(new String(sealed.ciphertext(), StandardCharsets.ISO_8859_1)).doesNotContain(API_KEY);
    }

    @Test
    void sealsUnderAFreshNonceEveryTime() {
        SecretBox.Sealed first = box.seal(API_KEY);
        SecretBox.Sealed second = box.seal(API_KEY);

        assertThat(first.nonce()).hasSize(12);
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void takesItsNonceFromTheInjectedRandom() {
        SecureRandom sevens = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 7);
            }
        };

        SecretBox.Sealed sealed = new SecretBox(KEY, sevens).seal(API_KEY);

        byte[] expected = new byte[12];
        Arrays.fill(expected, (byte) 7);
        assertThat(sealed.nonce()).isEqualTo(expected);
    }

    @Test
    void aDifferentKeyFailsInsteadOfOpeningAsGarbage() {
        SecretBox.Sealed sealed = box.seal(API_KEY);
        SecretBox rotated = new SecretBox(OTHER_KEY, new SecureRandom());

        assertThatThrownBy(() -> rotated.open(sealed)).isInstanceOf(UnreadableSecretException.class);
    }

    @Test
    void anAlteredCiphertextFails() {
        SecretBox.Sealed sealed = box.seal(API_KEY);
        byte[] altered = sealed.ciphertext().clone();
        altered[0] ^= 1;

        assertThatThrownBy(() -> box.open(new SecretBox.Sealed(altered, sealed.nonce())))
                .isInstanceOf(UnreadableSecretException.class);
    }

    @Test
    void rejectsAKeyThatIsNotThirtyTwoBytesOfBase64() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new SecretBox(shortKey, new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(shortKey);
        assertThatThrownBy(() -> new SecretBox("not base64!", new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not base64")
                .hasMessageNotContaining("not base64!");
    }

    static String base64Key(int fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
