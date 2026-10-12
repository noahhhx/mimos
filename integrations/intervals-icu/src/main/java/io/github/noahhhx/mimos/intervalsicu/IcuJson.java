package io.github.noahhhx.mimos.intervalsicu;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The parts of intervals.icu's responses Mimos reads, named as its OpenAPI
 * spec names them. Every field may be missing; the translator decides
 * what a missing field means.
 */
final class IcuJson {

    static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private IcuJson() {}

    record Event(
            @Nullable Long id,
            @JsonProperty("start_date_local") @Nullable String startDateLocal,
            @Nullable String type,
            @JsonProperty("moving_time") @Nullable Integer movingTime,
            @Nullable Long joules,
            @JsonProperty("icu_intensity") @Nullable Double icuIntensity) {}

    record Activity(
            @Nullable String id,
            @JsonProperty("start_date_local") @Nullable String startDateLocal,
            @Nullable String type,
            @JsonProperty("moving_time") @Nullable Integer movingTime,
            @JsonProperty("icu_joules") @Nullable Long icuJoules,
            @Nullable Integer calories) {}

    /** One day's record; its {@code id} is the local date. */
    record Wellness(
            @Nullable String id,
            @Nullable Double weight,
            @Nullable Double bodyFat) {}

    record Athlete(
            @Nullable String sex,
            @Nullable Double weight,
            @Nullable List<SportSettings> sportSettings) {}

    record SportSettings(
            @Nullable List<String> types, @Nullable Integer ftp) {}
}
