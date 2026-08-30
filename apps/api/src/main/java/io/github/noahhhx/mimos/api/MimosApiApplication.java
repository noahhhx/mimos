package io.github.noahhhx.mimos.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single backend deployable. Component scanning spans all Mimos modules
 * (io.github.noahhhx.mimos) so domain modules' services and repositories —
 * e.g. core-recipes — are picked up without per-module configuration.
 */
@SpringBootApplication(scanBasePackages = "io.github.noahhhx.mimos")
public class MimosApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MimosApiApplication.class, args);
    }
}
