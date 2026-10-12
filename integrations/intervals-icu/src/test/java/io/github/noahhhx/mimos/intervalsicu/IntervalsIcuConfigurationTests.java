package io.github.noahhhx.mimos.intervalsicu;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IntervalsIcuConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(IntervalsIcuConfiguration.class)
            .withBean(SecureRandom.class, SecureRandom::new);

    @Test
    void aKeyTurnsTheFeatureOn() {
        runner.withPropertyValues("mimos.intervals-icu.secret-key=" + SecretBoxTests.KEY)
                .run(context -> {
                    assertThat(context.getBean(IntervalsIcuProperties.class).active())
                            .isTrue();
                    assertThat(context).hasSingleBean(SecretBox.class);
                });
    }

    @Test
    void noKeyTurnsTheFeatureOff() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(IntervalsIcuProperties.class).active()).isFalse();
            assertThat(context).doesNotHaveBean(SecretBox.class);
        });
    }

    @Test
    void anEmptyKeyTurnsTheFeatureOff() {
        // application.yml maps an unset MIMOS_SECRET_KEY to an empty value.
        runner.withPropertyValues("mimos.intervals-icu.secret-key=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecretBox.class);
        });
    }

    @Test
    void theSwitchTurnsTheFeatureOffWithAKey() {
        runner.withPropertyValues(
                        "mimos.intervals-icu.enabled=false", "mimos.intervals-icu.secret-key=" + SecretBoxTests.KEY)
                .run(context -> {
                    assertThat(context.getBean(IntervalsIcuProperties.class).active())
                            .isFalse();
                    assertThat(context).doesNotHaveBean(SecretBox.class);
                });
    }

    @Test
    void aMalformedKeyFailsStartupWithoutEchoingIt() {
        runner.withPropertyValues("mimos.intervals-icu.secret-key=c2hvcnQta2V5").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("32 bytes")
                    .hasMessageNotContaining("c2hvcnQta2V5");
        });
    }

    @Test
    void theKeyNeverShowsInThePropertiesDescription() {
        runner.withPropertyValues("mimos.intervals-icu.secret-key=" + SecretBoxTests.KEY)
                .run(context -> assertThat(
                                context.getBean(IntervalsIcuProperties.class).toString())
                        .doesNotContain(SecretBoxTests.KEY));
    }
}
