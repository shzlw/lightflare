package com.lightflare.server.tools.openmeteo;

record ForecastResponse(
        String timezone,
        CurrentForecast current,
        DailyForecast daily
) {
}
