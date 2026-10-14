package io.github.noahhhx.mimos.openfoodfacts;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenFoodFactsConfigurationTests {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(OpenFoodFactsConfiguration.class);

    @Test
    void onByDefaultAgainstTheRealService() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(OpenFoodFactsClient.class);
            assertThat(context.getBean(OpenFoodFactsProperties.class).baseUrl())
                    .isEqualTo(URI.create("https://world.openfoodfacts.org"));
        });
    }

    @Test
    void theSwitchTurnsTheClientOff() {
        runner.withPropertyValues("mimos.open-food-facts.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OpenFoodFactsClient.class));
    }

    @Test
    void aBaseUrlThatIsNotHttpFailsStartup() {
        runner.withPropertyValues("mimos.open-food-facts.base-url=file:///etc/passwd")
                .run(context ->
                        assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("must be http(s)"));
    }
}
