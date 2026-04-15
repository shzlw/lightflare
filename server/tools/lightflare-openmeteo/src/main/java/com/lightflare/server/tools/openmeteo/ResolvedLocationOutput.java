package com.lightflare.server.tools.openmeteo;

record ResolvedLocationOutput(
        String name,
        String admin1,
        String admin2,
        String country,
        String countryCode,
        Double latitude,
        Double longitude,
        String timezone,
        Long population,
        String resolutionSource,
        String resolvedFrom
) {
}
