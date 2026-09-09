package io.github.noahhhx.mimos.plugins;

/** A plugin response that does not speak the extension API (ADR-0006). */
public class PluginProtocolException extends RuntimeException {

    public PluginProtocolException(String message) {
        super(message);
    }
}
