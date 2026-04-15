package com.lightflare.server.tools.openmeteo;

import com.fasterxml.jackson.annotation.JsonProperty;

record CurrentForecast(
        String time,
        @JsonProperty("temperature_2m") Double temperature2m,
        @JsonProperty("apparent_temperature") Double apparentTemperature,
        @JsonProperty("wind_speed_10m") Double windSpeed10m,
        @JsonProperty("weather_code") Integer weatherCode
) {
}
