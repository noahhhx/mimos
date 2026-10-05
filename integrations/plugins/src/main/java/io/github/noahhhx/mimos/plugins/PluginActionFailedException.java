package io.github.noahhhx.mimos.plugins;

/**
 * A plugin failed to answer a button press (ADR-0017). Unlike a failed
 * render, which only hides the panel, the user pressed something and must
 * hear that it did nothing, so the message is written for them.
 */
public class PluginActionFailedException extends RuntimeException {

    public PluginActionFailedException(String pluginName, Throwable cause) {
        super(pluginName + " did not answer. Try again.", cause);
    }
}
