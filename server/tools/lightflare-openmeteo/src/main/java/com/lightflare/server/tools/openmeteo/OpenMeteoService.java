package com.lightflare.server.tools.openmeteo;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
public class OpenMeteoService {

    private static final int MAX_FORECAST_DAYS = 16;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int GEOCODING_CANDIDATE_COUNT = 10;
    private static final int AMBIGUITY_SCORE_GAP = 15;

    private final RestClient geocodingRestClient;
    private final RestClient forecastRestClient;
    private final OpenMeteoProperties properties;

    public String getForecastByLocation(String location, Integer requestedForecastDays) {
        return getForecastByLocation(new OpenMeteoLocationRequest(location, null, null, null),
                requestedForecastDays);
    }

    public String geocodeLocation(OpenMeteoLocationRequest locationRequest) {
        OpenMeteoLocationRequest normalizedRequest = normalize(locationRequest);
        if (normalizedRequest.location() == null) {
            throw new IllegalArgumentException("Provide a location");
        }

        ResolvedLocation resolvedLocation = resolveLocation(normalizedRequest);
        try {
            return OBJECT_MAPPER.writeValueAsString(toResolvedLocationOutput(resolvedLocation));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize geocoding output", e);
        }
    }

    public String getForecastByLocation(OpenMeteoLocationRequest locationRequest, Integer requestedForecastDays) {
        OpenMeteoLocationRequest normalizedRequest = normalize(locationRequest);
        if (isEmpty(normalizedRequest)) {
            throw new IllegalArgumentException("Provide a location, postal code, or latitude/longitude");
        }

        int forecastDays = requestedForecastDays == null ? properties.defaultForecastDays() : requestedForecastDays;
        if (forecastDays < 1 || forecastDays > MAX_FORECAST_DAYS) {
            throw new IllegalArgumentException("forecast_days must be between 1 and " + MAX_FORECAST_DAYS);
        }

        ResolvedLocation resolvedLocation = resolveLocation(normalizedRequest);

        ForecastResponse forecastResponse = forecastRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/forecast")
                        .queryParam("latitude", resolvedLocation.result().latitude())
                        .queryParam("longitude", resolvedLocation.result().longitude())
                        .queryParam("current", "temperature_2m,apparent_temperature,weather_code,wind_speed_10m")
                        .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                        .queryParam("timezone", "auto")
                        .queryParam("forecast_days", forecastDays)
                        .build())
                .retrieve()
                .body(ForecastResponse.class);

        if (forecastResponse == null || forecastResponse.daily() == null || forecastResponse.daily().time() == null) {
            throw new IllegalStateException("Open-Meteo forecast response was empty");
        }

        ForecastOutput output = new ForecastOutput(
                toResolvedLocationOutput(resolvedLocation),
                forecastDays,
                forecastResponse.timezone(),
                forecastResponse.current() == null ? null : new CurrentForecastOutput(
                        forecastResponse.current().time(),
                        forecastResponse.current().temperature2m(),
                        forecastResponse.current().apparentTemperature(),
                        forecastResponse.current().windSpeed10m(),
                        forecastResponse.current().weatherCode(),
                        describeWeatherCode(forecastResponse.current().weatherCode())
                ),
                buildDailyForecasts(forecastResponse.daily())
        );

        try {
            return OBJECT_MAPPER.writeValueAsString(output);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize forecast output", e);
        }
    }

    ResolvedLocation resolveLocation(OpenMeteoLocationRequest request) {
        if (request.latitude() != null && request.longitude() != null) {
            return new ResolvedLocation(
                    new GeocodingResult(
                            "Custom coordinates",
                            request.latitude(),
                            request.longitude(),
                            null,
                            normalizeUpper(request.countryCode()),
                            null,
                            null,
                            null,
                            null
                    ),
                    "coordinates",
                    request.latitude() + "," + request.longitude()
            );
        }

        String query = buildQuery(request);
        GeocodingResponse geocodingResponse = geocodingRestClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v1/search")
                            .queryParam("name", query)
                            .queryParam("count", GEOCODING_CANDIDATE_COUNT)
                            .queryParam("language", "en")
                            .queryParam("format", "json");
        if (request.countryCode() != null) {
            uriBuilder.queryParam("countryCode", request.countryCode());
        }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(GeocodingResponse.class);

        List<GeocodingResult> candidates = geocodingResponse == null || geocodingResponse.results() == null
                ? List.of()
                : geocodingResponse.results().stream()
                .filter(Objects::nonNull)
                .filter(result -> result.latitude() != null && result.longitude() != null)
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No forecast location match found for '" + query + "'");
        }

        return selectBestLocation(request, query, candidates);
    }

    ResolvedLocation selectBestLocation(OpenMeteoLocationRequest request, String query, List<GeocodingResult> candidates) {
        List<ScoredCandidate> rankedCandidates = candidates.stream()
                .map(candidate -> new ScoredCandidate(candidate, scoreCandidate(request, query, candidate)))
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed()
                        .thenComparing(candidate -> Optional.ofNullable(candidate.result().population()).orElse(0L),
                                Comparator.reverseOrder())
                        .thenComparing(candidate -> normalize(candidate.result().name())))
                .toList();

        ScoredCandidate best = rankedCandidates.getFirst();
        ScoredCandidate second = rankedCandidates.size() > 1 ? rankedCandidates.get(1) : null;

        if (shouldRejectAsAmbiguous(request, best, second)) {
            throw new IllegalArgumentException(buildAmbiguousLocationMessage(query, rankedCandidates));
        }

        return new ResolvedLocation(best.result(), inferResolutionSource(request), query);
    }

    private List<DailyForecastOutput> buildDailyForecasts(DailyForecast daily) {
        int days = daily.time().size();
        return java.util.stream.IntStream.range(0, days)
                .mapToObj(index -> {
                    Integer weatherCode = read(daily.weatherCode(), index);
                    return new DailyForecastOutput(
                            daily.time().get(index),
                            read(daily.temperature2mMin(), index),
                            read(daily.temperature2mMax(), index),
                            read(daily.precipitationProbabilityMax(), index),
                            weatherCode,
                            describeWeatherCode(weatherCode)
                    );
                })
                .toList();
    }

    private ResolvedLocationOutput toResolvedLocationOutput(ResolvedLocation resolvedLocation) {
        return new ResolvedLocationOutput(
                resolvedLocation.result().name(),
                resolvedLocation.result().admin1(),
                resolvedLocation.result().admin2(),
                resolvedLocation.result().country(),
                resolvedLocation.result().countryCode(),
                resolvedLocation.result().latitude(),
                resolvedLocation.result().longitude(),
                resolvedLocation.result().timezone(),
                resolvedLocation.result().population(),
                resolvedLocation.source(),
                resolvedLocation.resolvedFrom()
        );
    }

    private static <T> T read(List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private OpenMeteoLocationRequest normalize(OpenMeteoLocationRequest request) {
        if (request == null) {
            return new OpenMeteoLocationRequest(null, null, null, null);
        }

        String normalizedLocation = normalizeText(request.location());
        String normalizedCountryCode = normalizeUpper(request.countryCode());
        Double latitude = request.latitude();
        Double longitude = request.longitude();

        return new OpenMeteoLocationRequest(
                normalizedLocation,
                normalizedCountryCode,
                latitude,
                longitude
        );
    }

    private boolean isEmpty(OpenMeteoLocationRequest request) {
        return request.location() == null
                && request.latitude() == null
                && request.longitude() == null;
    }

    private String buildQuery(OpenMeteoLocationRequest request) {
        if (request.location() != null) {
            return request.location();
        }

        throw new IllegalArgumentException("Provide a location or latitude/longitude");
    }

    private int scoreCandidate(OpenMeteoLocationRequest request, String query, GeocodingResult candidate) {
        int score = 0;
        String normalizedQuery = normalize(query);
        String normalizedName = normalize(candidate.name());
        String normalizedCountryCode = normalize(candidate.countryCode());

        if (normalizedQuery != null && normalizedQuery.equals(normalizedName)) {
            score += 120;
        } else if (normalizedQuery != null && normalizedName != null && normalizedName.startsWith(normalizedQuery)) {
            score += 70;
        }

        if (request.countryCode() != null && matches(request.countryCode(), normalizedCountryCode)) {
            score += 110;
        }

        if (request.location() != null && request.location().contains(",")) {
            List<String> tokens = tokenize(request.location());
            if (tokens.stream().anyMatch(token -> token.equals(normalizedName))) {
                score += 60;
            }
            if (tokens.stream().anyMatch(token -> token.equals(normalizedCountryCode))) {
                score += 80;
            }
        }

        long population = Optional.ofNullable(candidate.population()).orElse(0L);
        if (population > 0) {
            score += Math.min(30, (int) Math.log10(population));
        }
        return score;
    }

    private boolean shouldRejectAsAmbiguous(OpenMeteoLocationRequest request,
                                            ScoredCandidate best,
                                            ScoredCandidate second) {
        if (second == null) {
            return false;
        }
        if (request.countryCode() != null) {
            return false;
        }

        boolean sameName = matches(best.result().name(), normalize(second.result().name()));
        boolean closeScore = best.score() - second.score() <= AMBIGUITY_SCORE_GAP;
        return sameName && closeScore;
    }

    private String buildAmbiguousLocationMessage(String query, List<ScoredCandidate> rankedCandidates) {
        String suggestions = rankedCandidates.stream()
                .limit(3)
                .map(candidate -> formatCandidate(candidate.result()))
                .reduce((left, right) -> left + " | " + right)
                .orElse("No candidates available");
        return "Ambiguous location '" + query
                + "'. Add a state, country, postal code, or coordinates. Top matches: "
                + suggestions;
    }

    private String inferResolutionSource(OpenMeteoLocationRequest request) {
        if (request.latitude() != null && request.longitude() != null) {
            return "coordinates";
        }
        if (request.countryCode() != null) {
            return "location_with_country_code";
        }
        return "location_text";
    }

    private List<String> tokenize(String value) {
        return java.util.Arrays.stream(value.split(","))
                .map(this::normalize)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean matches(String expected, String normalizedActual) {
        String normalizedExpected = normalize(expected);
        return normalizedExpected != null && normalizedExpected.equals(normalizedActual);
    }

    private String formatCandidate(GeocodingResult result) {
        return java.util.stream.Stream.of(result.name(), result.admin1(), result.country())
                .filter(Objects::nonNull)
                .reduce((left, right) -> left + ", " + right)
                .orElse("Unknown");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private String normalize(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private static String describeWeatherCode(Integer code) {
        if (code == null) {
            return "unknown";
        }
        return WEATHER_CODES.getOrDefault(code, "unknown");
    }

    private static final Map<Integer, String> WEATHER_CODES = Map.ofEntries(
            Map.entry(0, "clear sky"),
            Map.entry(1, "mainly clear"),
            Map.entry(2, "partly cloudy"),
            Map.entry(3, "overcast"),
            Map.entry(45, "fog"),
            Map.entry(48, "depositing rime fog"),
            Map.entry(51, "light drizzle"),
            Map.entry(53, "moderate drizzle"),
            Map.entry(55, "dense drizzle"),
            Map.entry(56, "light freezing drizzle"),
            Map.entry(57, "dense freezing drizzle"),
            Map.entry(61, "slight rain"),
            Map.entry(63, "moderate rain"),
            Map.entry(65, "heavy rain"),
            Map.entry(66, "light freezing rain"),
            Map.entry(67, "heavy freezing rain"),
            Map.entry(71, "slight snow fall"),
            Map.entry(73, "moderate snow fall"),
            Map.entry(75, "heavy snow fall"),
            Map.entry(77, "snow grains"),
            Map.entry(80, "slight rain showers"),
            Map.entry(81, "moderate rain showers"),
            Map.entry(82, "violent rain showers"),
            Map.entry(85, "slight snow showers"),
            Map.entry(86, "heavy snow showers"),
            Map.entry(95, "thunderstorm"),
            Map.entry(96, "thunderstorm with slight hail"),
            Map.entry(99, "thunderstorm with heavy hail")
    );

    record ResolvedLocation(GeocodingResult result, String source, String resolvedFrom) {
    }

    private record ScoredCandidate(GeocodingResult result, int score) {
    }
}
