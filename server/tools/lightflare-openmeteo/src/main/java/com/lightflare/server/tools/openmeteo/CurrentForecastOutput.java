package com.lightflare.server.tools.openmeteo;

record CurrentForecastOutput(
        String time,
        Double temperature2m,
        Double apparentTemperature,
        Double windSpeed10m,
        Integer weatherCode,
        String weatherDescription
) {
}
