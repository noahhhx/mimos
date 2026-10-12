package io.github.noahhhx.mimos.intervalsicu;

/** Whether a saved connection still works. A person who never connected has no connection at all. */
public enum ConnectionStatus {
    CONNECTED,
    /** intervals.icu stopped accepting the key, or this instance can no longer read it: enter it again. */
    BROKEN
}
