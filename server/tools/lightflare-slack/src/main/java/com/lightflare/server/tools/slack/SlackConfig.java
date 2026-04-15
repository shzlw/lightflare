package com.lightflare.server.tools.slack;

import com.lightflare.server.messaging.MessagingAppConnector;
import com.lightflare.server.messaging.MessagingAppUserIdentityProvider;
import com.lightflare.server.messaging.MessagingAppUserResolver;
import com.slack.api.Slack;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

import java.util.concurrent.Executor;

// https://docs.slack.dev/tools/java-slack-sdk/guides/getting-started-with-bolt
// SLACK_BOT_TOKEN, SLACK_SIGNING_SECRET, SLACK_APP_TOKEN

/**
 * Event subscriptions — in your Slack app dashboard under Event Subscriptions, make sure the events your handler listens for (e.g. app_mention, message.channels) are actually subscribed. Socket Mode still requires you to subscribe to events there.
 * Bot scopes — app_mention requires app_mentions:read scope, message.channels requires channels:history. Check OAuth & Permissions → Bot Token Scopes.
 * Bot is in the channel — the bot must be invited to the channel (/invite @yourbot) or it won't receive message events from it.
 * Reinstall after scope changes — any time you add a new scope, you need to reinstall the app to the workspace for the new scope to take effect.
 */
@Configuration
@EnableConfigurationProperties(SlackProperties.class)
@ConditionalOnProperty(
        prefix = "lightflare.tools.slack",
        name = "enabled",
        havingValue = "true"
)
@Slf4j
public class SlackConfig {

    private final SlackProperties slackProperties;

    public SlackConfig(SlackProperties slackProperties) {
        this.slackProperties = slackProperties;
    }

    @Bean
    public AppConfig appConfig() {
        Assert.hasText(slackProperties.getBotToken(), "Slack bot token is required when Slack integration is enabled");
        Assert.hasText(slackProperties.getSigningSecret(), "Slack signing secret is required when Slack integration is enabled");
        Assert.hasText(slackProperties.getAppToken(), "Slack app token is required when Slack integration is enabled");
        return AppConfig.builder()
                .singleTeamBotToken(slackProperties.getBotToken())
                .signingSecret(slackProperties.getSigningSecret())
                .build();
    }

    @Bean
    public App slackApp(AppConfig appConfig) {
        App app = new App(appConfig);
        return app;
    }

    @Bean
    public SocketModeApp socketModeApp(App slackApp) throws Exception {
        return new SocketModeApp(slackProperties.getAppToken(), slackApp);
    }

    @Bean
    public Slack slack() {
        return Slack.getInstance();
    }

    @Bean
    public SlackSocketModeRunner slackSocketModeRunner(SocketModeApp socketModeApp) {
        return new SlackSocketModeRunner(socketModeApp);
    }

    @Bean
    public Executor slackAgentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("slack-agent-");
        executor.setCorePoolSize(Math.max(1, slackProperties.getAgentExecutorCorePoolSize()));
        executor.setMaxPoolSize(Math.max(
                Math.max(1, slackProperties.getAgentExecutorCorePoolSize()),
                slackProperties.getAgentExecutorMaxPoolSize()
        ));
        executor.setQueueCapacity(Math.max(1, slackProperties.getAgentExecutorQueueCapacity()));
        executor.initialize();
        return executor;
    }

    @Bean
    public MessagingAppUserIdentityProvider slackMessagingAppUserIdentityProvider() {
        return new SlackMessagingAppUserIdentityProvider();
    }

    @Bean
    public SlackChannelPolicy slackChannelPolicy(SlackChannelResolver slackChannelResolver) {
        return new SlackChannelPolicy(slackProperties, slackChannelResolver);
    }

    @Bean
    public SlackEventHandler slackEventHandler(App app,
                                               SlackChannelPolicy slackChannelPolicy,
                                               MessagingAppUserResolver slackMessagingAppUserResolver,
                                               MessagingAppConnector messagingAppConnector,
                                               SlackMessageService slackMessageService,
                                               @Qualifier("slackAgentExecutor") Executor slackAgentExecutor) {
        return new SlackEventHandler(
                app,
                slackProperties,
                slackChannelPolicy,
                slackMessagingAppUserResolver,
                messagingAppConnector,
                slackMessageService,
                slackAgentExecutor
        );
    }

    @Bean
    public SlackMessageService slackMessageService(Slack slack) {
        return new SlackMessageService(slack, slackProperties.getBotToken());
    }

    @Bean
    public SlackChannelResolver slackChannelResolver(Slack slack) {
        return new SlackChannelResolver(slack, slackProperties.getBotToken());
    }

}
