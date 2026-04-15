package com.lightflare.server.tools.openmeteo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenMeteoProperties.class)
@ConditionalOnProperty(prefix = "lightflare.tools.openmeteo", name = "enabled", havingValue = "true")
public class OpenMeteoConfig {

    @Bean
    RestClient openMeteoGeocodingRestClient(OpenMeteoProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.geocodingBaseUrl())
                .build();
    }

    @Bean
    RestClient openMeteoForecastRestClient(OpenMeteoProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.forecastBaseUrl())
                .build();
    }

    @Bean
    OpenMeteoService openMeteoService(
            @Qualifier("openMeteoGeocodingRestClient") RestClient openMeteoGeocodingRestClient,
            @Qualifier("openMeteoForecastRestClient") RestClient openMeteoForecastRestClient,
            OpenMeteoProperties properties
    ) {
        return new OpenMeteoService(
                openMeteoGeocodingRestClient,
                openMeteoForecastRestClient,
                properties
        );
    }

    @Bean
    WeatherForecastTool weatherForecastTool(OpenMeteoService openMeteoService) {
        return new WeatherForecastTool(openMeteoService);
    }

    @Bean
    GeocodingTool geocodingTool(OpenMeteoService openMeteoService) {
        return new GeocodingTool(openMeteoService);
    }
}
