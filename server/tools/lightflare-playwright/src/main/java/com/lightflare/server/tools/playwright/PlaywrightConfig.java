package com.lightflare.server.tools.playwright;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlaywrightProperties.class)
@ConditionalOnProperty(prefix = "lightflare.tools.playwright", name = "enabled", havingValue = "true")
public class PlaywrightConfig {

    @Bean
    PlaywrightUrlPolicy playwrightUrlPolicy(PlaywrightProperties properties) {
        return new PlaywrightUrlPolicy(properties);
    }

    @Bean
    PlaywrightWorkerPool playwrightWorkerPool(PlaywrightProperties properties, PlaywrightUrlPolicy urlPolicy) {
        return new PlaywrightWorkerPool(properties, urlPolicy);
    }

    @Bean
    PlaywrightService playwrightService(PlaywrightWorkerPool workerPool, PlaywrightUrlPolicy urlPolicy) {
        return new PlaywrightService(workerPool, urlPolicy);
    }

    @Bean
    WebPageContentExtractor webPageContentExtractor(PlaywrightService playwrightService) {
        return new WebPageContentExtractor(playwrightService);
    }

    @Bean
    WebPageContentExtractorTool webPageContentExtractorTool(WebPageContentExtractor webPageContentExtractor) {
        return new WebPageContentExtractorTool(webPageContentExtractor);
    }
}
