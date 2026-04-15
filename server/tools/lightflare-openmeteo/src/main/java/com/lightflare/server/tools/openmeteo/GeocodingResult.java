package com.lightflare.server.tools.openmeteo;

record GeocodingResult(
        String name,
        Double latitude,
        Double longitude,
        String country,
        String countryCode,
        String admin1,
        String admin2,
        Long population,
        String timezone
) {
}
