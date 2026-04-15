package com.lightflare.server.tools.openmeteo;

record OpenMeteoLocationRequest(
        String location,
        String countryCode,
        Double latitude,
        Double longitude
) {
}
