package com.lightflare.server.tools.openmeteo;

import java.util.List;

record ForecastOutput(
        ResolvedLocationOutput location,
        int forecastDays,
        String timezone,
        CurrentForecastOutput current,
        List<DailyForecastOutput> daily
) {
}
