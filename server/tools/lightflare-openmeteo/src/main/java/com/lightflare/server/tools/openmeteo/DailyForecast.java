package com.lightflare.server.tools.openmeteo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record DailyForecast(
        List<String> time,
        @JsonProperty("temperature_2m_min") List<Double> temperature2mMin,
        @JsonProperty("temperature_2m_max") List<Double> temperature2mMax,
        @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax,
        @JsonProperty("weather_code") List<Integer> weatherCode
) {
}
