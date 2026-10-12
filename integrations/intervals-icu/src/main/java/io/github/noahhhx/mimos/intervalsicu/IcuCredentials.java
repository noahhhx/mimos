package io.github.noahhhx.mimos.intervalsicu;

/** One person's athlete id and personal API key. */
record IcuCredentials(String athleteId, String apiKey) {

    @Override
    public String toString() {
        return "IcuCredentials[athleteId=" + athleteId + ", apiKey=<hidden>]";
    }
}
