package com.lightflare.server.tools.openmeteo;

record DailyForecastOutput(
        String date,
        Double minTemperature2m,
        Double maxTemperature2m,
        Integer precipitationProbabilityMax,
        Integer weatherCode,
        String weatherDescription
) {
}
