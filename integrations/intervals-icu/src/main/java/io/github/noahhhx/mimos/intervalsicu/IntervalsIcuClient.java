package io.github.noahhhx.mimos.intervalsicu;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.core.JacksonException;

/**
 * Reads one person's intervals.icu data with their personal API key: HTTP
 * Basic with the username {@code API_KEY} (ADR-0022). Every call answers
 * with an {@link IcuResult}; nothing here throws for what intervals.icu
 * or the network did.
 */
final class IntervalsIcuClient {

    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;

    IntervalsIcuClient(URI baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Planned workouts on the athlete's calendar, {@code oldest} to {@code newest} inclusive. */
    IcuResult<List<IcuJson.Event>> plannedWorkouts(IcuCredentials credentials, LocalDate oldest, LocalDate newest) {
        return get(
                credentials,
                uri -> uri.path("/api/v1/athlete/{id}/events")
                        .queryParam("oldest", oldest)
                        .queryParam("newest", newest)
                        .queryParam("category", "WORKOUT")
                        .build(credentials.athleteId()),
                IcuJson.Event[].class,
                List::of);
    }

    /** Completed activities, {@code oldest} to {@code newest} inclusive. */
    IcuResult<List<IcuJson.Activity>> activities(IcuCredentials credentials, LocalDate oldest, LocalDate newest) {
        return get(
                credentials,
                uri -> uri.path("/api/v1/athlete/{id}/activities")
                        .queryParam("oldest", oldest)
                        .queryParam("newest", newest)
                        .build(credentials.athleteId()),
                IcuJson.Activity[].class,
                List::of);
    }

    /** Wellness records, one per day, {@code oldest} to {@code newest} inclusive. */
    IcuResult<List<IcuJson.Wellness>> wellness(IcuCredentials credentials, LocalDate oldest, LocalDate newest) {
        return get(
                credentials,
                uri -> uri.path("/api/v1/athlete/{id}/wellness")
                        .queryParam("oldest", oldest)
                        .queryParam("newest", newest)
                        .build(credentials.athleteId()),
                IcuJson.Wellness[].class,
                List::of);
    }

    /** The athlete record. A key that reads it is a key that works. */
    IcuResult<IcuJson.Athlete> athlete(IcuCredentials credentials) {
        return get(
                credentials,
                uri -> uri.path("/api/v1/athlete/{id}").build(credentials.athleteId()),
                IcuJson.Athlete.class,
                Function.identity());
    }

    private <B, T> IcuResult<T> get(
            IcuCredentials credentials, Function<UriBuilder, URI> uri, Class<B> bodyType, Function<B, T> toValue) {
        try {
            return restClient
                    .get()
                    .uri(uri)
                    .headers(headers -> headers.setBasicAuth("API_KEY", credentials.apiKey()))
                    .exchange((request, response) -> {
                        HttpStatus status =
                                HttpStatus.resolve(response.getStatusCode().value());
                        if (status == HttpStatus.UNAUTHORIZED) {
                            return new IcuResult.Unauthorized<>();
                        }
                        if (status == HttpStatus.TOO_MANY_REQUESTS) {
                            return new IcuResult.RateLimited<>(
                                    retryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return new IcuResult.Failed<>("intervals.icu answered "
                                    + response.getStatusCode().value());
                        }
                        @Nullable B body = IcuJson.MAPPER.readValue(response.getBody(), bodyType);
                        if (body == null) {
                            return new IcuResult.Failed<>("intervals.icu answered with an empty body");
                        }
                        return new IcuResult.Ok<>(toValue.apply(body));
                    });
        } catch (JacksonException e) {
            return new IcuResult.Failed<>("intervals.icu answered with unreadable JSON");
        } catch (RestClientException e) {
            return new IcuResult.Failed<>("intervals.icu did not answer: " + e.getMessage());
        }
    }

    /** {@code Retry-After} in delta-seconds; an HTTP date or anything else reads as unknown. */
    private static @Nullable Duration retryAfter(@Nullable String header) {
        if (header == null) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
