package com.lightflare.server.tools.openmeteo;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenMeteoServiceTest {

    private final OpenMeteoService service = new OpenMeteoService(
            RestClient.builder().baseUrl("https://example.com").build(),
            RestClient.builder().baseUrl("https://example.com").build(),
            new OpenMeteoProperties(true, java.util.List.of("weather-forecast", "geocoding"), null, null, 3)
    );

    @Test
    void choosesCountryCodeMatchOverPopulation() {
        OpenMeteoService.ResolvedLocation resolvedLocation = service.selectBestLocation(
                new OpenMeteoLocationRequest("Paris", "FR", null, null),
                "Paris",
                List.of(
                        new GeocodingResult("Paris", 48.8566, 2.3522, "France", "FR", "Ile-de-France", "Paris", 2100000L, "Europe/Paris"),
                        new GeocodingResult("Paris", 33.6609, -95.5555, "United States", "US", "Texas", "Lamar County", 25000L, "America/Chicago")
                )
        );

        assertEquals("FR", resolvedLocation.result().countryCode());
        assertEquals("location_with_country_code", resolvedLocation.source());
    }

    @Test
    void prefersCountryCodeWhenNamesMatch() {
        OpenMeteoService.ResolvedLocation resolvedLocation = service.selectBestLocation(
                new OpenMeteoLocationRequest("Frisco", "US", null, null),
                "Frisco",
                List.of(
                        new GeocodingResult("Frisco", 33.1, -96.8, "United States", "US", "Texas", "Collin County", 210000L, "America/Chicago"),
                        new GeocodingResult("Frisco", 8.5, 124.7, "Philippines", "PH", "Northern Mindanao", "Misamis Oriental", 0L, "Asia/Manila")
                )
        );

        assertEquals("US", resolvedLocation.result().countryCode());
        assertEquals("location_with_country_code", resolvedLocation.source());
    }

    @Test
    void rejectsAmbiguousUnqualifiedLocationNames() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.selectBestLocation(
                new OpenMeteoLocationRequest("Springfield", null, null, null),
                "Springfield",
                List.of(
                        new GeocodingResult("Springfield", 39.8, -89.6, "United States", "US", "Illinois", "Sangamon County", 114000L, "America/Chicago"),
                        new GeocodingResult("Springfield", 37.2, -93.3, "United States", "US", "Missouri", "Greene County", 170000L, "America/Chicago")
                )
        ));

        assertEquals(true, error.getMessage().startsWith("Ambiguous location 'Springfield'"));
    }

    @Test
    void usesCoordinatesWithoutGeocoding() {
        OpenMeteoService.ResolvedLocation resolvedLocation = service.resolveLocation(
                new OpenMeteoLocationRequest(null, "US", 32.7767, -96.7970)
        );

        assertEquals("Custom coordinates", resolvedLocation.result().name());
        assertEquals("coordinates", resolvedLocation.source());
        assertEquals(32.7767, resolvedLocation.result().latitude());
    }
}
