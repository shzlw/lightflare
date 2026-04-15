package com.lightflare.server.tools.playwright;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PlaywrightService {

    private final PlaywrightWorkerPool playwrightWorkerPool;
    private final PlaywrightUrlPolicy playwrightUrlPolicy;

    public String fetchPageContent(String url) {
        return playwrightWorkerPool.fetchPageContent(playwrightUrlPolicy.validateNavigationTarget(url).toString());
    }
}
