package com.lightflare.server.tools.httpclient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.HttpURLConnection;

@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
@ConditionalOnProperty(prefix = "lightflare.tools.httpclient", name = "enabled", havingValue = "true")
public class HttpClientConfig {

    @Bean("lightFlareToolHttpClientRestClient")
    RestClient httpClientRestClient(HttpClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    HttpClientService httpClientService(
            @Qualifier("lightFlareToolHttpClientRestClient") RestClient httpClientRestClient,
            HttpClientProperties properties
    ) {
        return new HttpClientService(httpClientRestClient, properties);
    }

    @Bean
    HttpGetTool httpGetTool(HttpClientService httpClientService) {
        return new HttpGetTool(httpClientService);
    }

    @Bean
    HttpPostTool httpPostTool(HttpClientService httpClientService) {
        return new HttpPostTool(httpClientService);
    }

    @Bean
    HttpPutTool httpPutTool(HttpClientService httpClientService) {
        return new HttpPutTool(httpClientService);
    }

    @Bean
    HttpDeleteTool httpDeleteTool(HttpClientService httpClientService) {
        return new HttpDeleteTool(httpClientService);
    }
}
