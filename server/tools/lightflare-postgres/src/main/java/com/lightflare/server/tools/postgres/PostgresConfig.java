package com.lightflare.server.tools.postgres;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.lightflare.server.integration.Integration;

@Configuration
@EnableConfigurationProperties(PostgresProperties.class)
@ConditionalOnProperty(prefix = "lightflare.tools.postgres", name = "enabled", havingValue = "true")
public class PostgresConfig {

    @Bean
    public PostgresService postgresService(PostgresProperties properties) {
        return new PostgresService(properties);
    }

    @Bean
    public Integration postgresIntegration(PostgresProperties properties) {
        return new PostgresIntegration(properties);
    }

    @Bean
    public PostgresListTablesTool postgresListTablesTool(PostgresService postgresService) {
        return new PostgresListTablesTool(postgresService);
    }

    @Bean
    public PostgresListColumnsTool postgresListColumnsTool(PostgresService postgresService) {
        return new PostgresListColumnsTool(postgresService);
    }

    @Bean
    public PostgresRunQueryTool postgresRunQueryTool(PostgresService postgresService) {
        return new PostgresRunQueryTool(postgresService);
    }
}
