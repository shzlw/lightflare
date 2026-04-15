package com.lightflare.server.tools.playwright;

import com.lightflare.server.tools.core.ToolSelection;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "lightflare.tools.playwright")
public class PlaywrightProperties implements ToolSelection {

    private boolean enabled = false;
    private List<String> enabledTools = List.of();

    private int workerCount = 2;
    private int maxPendingTasksPerWorker = 8;
    private long queueAcquireTimeoutMs = 1000;
    private double pageTimeoutMs = 10000;
    private boolean headless = true;
    private boolean acceptDownloads = false;
    private boolean blockPrivateNetworkTargets = true;
    private List<String> allowedSchemes = List.of("http", "https");
    private List<String> allowedHosts = List.of();

    @Override
    public String integrationId() {
        return "playwright";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public List<String> enabledTools() {
        return enabledTools;
    }
}
