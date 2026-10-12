package io.github.noahhhx.mimos.intervalsicu;

/** intervals.icu did not answer, or asked to wait, so nothing could be checked or saved. */
public class IntervalsIcuUnavailableException extends RuntimeException {

    IntervalsIcuUnavailableException(String message) {
        super(message);
    }
}
