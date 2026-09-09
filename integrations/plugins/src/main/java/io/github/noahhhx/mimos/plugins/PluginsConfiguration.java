package io.github.noahhhx.mimos.plugins;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link PluginsProperties}; the registry itself is a plain component. */
@Configuration
@EnableConfigurationProperties(PluginsProperties.class)
public class PluginsConfiguration {}
