package com.lightflare.server.tools.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
public class PlaywrightWorkerPool implements DisposableBean {

    private final PlaywrightProperties properties;
    private final PlaywrightUrlPolicy urlPolicy;
    private final List<PlaywrightWorker> workers;
    private final Semaphore capacityLimiter;
    private final AtomicInteger nextWorkerIndex = new AtomicInteger();

    public PlaywrightWorkerPool(PlaywrightProperties properties, PlaywrightUrlPolicy urlPolicy) {
        this.properties = properties;
        this.urlPolicy = urlPolicy;
        int workerCount = Math.max(1, properties.getWorkerCount());
        int maxPendingTasksPerWorker = Math.max(1, properties.getMaxPendingTasksPerWorker());
        this.capacityLimiter = new Semaphore(workerCount * maxPendingTasksPerWorker, true);
        this.workers = IntStream.range(0, workerCount)
                .mapToObj(workerId -> new PlaywrightWorker(workerId, properties, urlPolicy))
                .toList();
        log.info("Initialized Playwright worker pool with workerCount={}, maxPendingTasksPerWorker={}",
                workerCount, maxPendingTasksPerWorker);
    }

    public String fetchPageContent(String url) {
        boolean acquired = false;
        try {
            acquired = capacityLimiter.tryAcquire(
                    Math.max(1L, properties.getQueueAcquireTimeoutMs()),
                    TimeUnit.MILLISECONDS
            );
            if (!acquired) {
                throw new IllegalStateException("Playwright worker pool is saturated. Try again later.");
            }

            PlaywrightWorker worker = selectWorker();
            Future<String> future = worker.submit(() -> worker.fetchPageContent(url), "execute fetch for " + url);
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Playwright execution", e);
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("Playwright worker pool is shutting down", e);
        } catch (ExecutionException e) {
            throw unwrap(e);
        } finally {
            if (acquired) {
                capacityLimiter.release();
            }
        }
    }

    private PlaywrightWorker selectWorker() {
        int workerIndex = Math.floorMod(nextWorkerIndex.getAndIncrement(), workers.size());
        return workers.get(workerIndex);
    }

    @Override
    public void destroy() {
        for (PlaywrightWorker worker : workers) {
            worker.close();
        }
    }

    private RuntimeException unwrap(ExecutionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Playwright execution failed", cause);
    }

    private static final class PlaywrightWorker {

        private final int workerId;
        private final PlaywrightProperties properties;
        private final PlaywrightUrlPolicy urlPolicy;
        private final ExecutorService executor;

        private Playwright playwright;
        private Browser browser;

        private PlaywrightWorker(int workerId, PlaywrightProperties properties, PlaywrightUrlPolicy urlPolicy) {
            this.workerId = workerId;
            this.properties = properties;
            this.urlPolicy = urlPolicy;
            this.executor = Executors.newSingleThreadExecutor(new PlaywrightThreadFactory(workerId));
            initialize();
        }

        private void initialize() {
            runOnWorker(() -> {
                playwright = Playwright.create();
                browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(properties.isHeadless())
                );
                log.info("Started Playwright worker={}", workerId);
                return null;
            }, "start");
        }

        private String fetchPageContent(String url) {
            log.info("Worker {} fetching page content for url={}", workerId, url);
            try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setAcceptDownloads(properties.isAcceptDownloads())
                    .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
                    .setIgnoreHTTPSErrors(false));
                 Page page = context.newPage()) {
                context.route("**/*", route -> {
                    String requestUrl = route.request().url();
                    String resourceType = route.request().resourceType();
                    if (!urlPolicy.isAllowedRequestUrl(requestUrl)
                            || isBlockedResourceType(resourceType)
                            || isBlockedDownloadCandidate(requestUrl)) {
                        route.abort();
                        return;
                    }
                    route.resume();
                });
                page.onDownload(download -> {
                    download.cancel();
                    log.warn("Worker {} blocked download for url={}", workerId, download.url());
                });
                page.onPopup(Page::close);
                page.setDefaultNavigationTimeout(properties.getPageTimeoutMs());
                page.setDefaultTimeout(properties.getPageTimeoutMs());
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(properties.getPageTimeoutMs())
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                String content = page.content();
                log.info("Worker {} fetched page content for url={}, contentLength={}",
                        workerId, url, content.length());
                return content;
            } catch (PlaywrightException e) {
                log.info("Worker {} failed to fetch page content for url={}", workerId, url);
                throw new IllegalStateException("Failed to fetch page content from " + url, e);
            }
        }

        private boolean isBlockedResourceType(String resourceType) {
            return "media".equals(resourceType)
                    || "eventsource".equals(resourceType)
                    || "websocket".equals(resourceType)
                    || "manifest".equals(resourceType);
        }

        private boolean isBlockedDownloadCandidate(String requestUrl) {
            String normalizedUrl = requestUrl.toLowerCase();
            return normalizedUrl.endsWith(".zip")
                    || normalizedUrl.endsWith(".exe")
                    || normalizedUrl.endsWith(".dmg")
                    || normalizedUrl.endsWith(".pkg")
                    || normalizedUrl.endsWith(".msi")
                    || normalizedUrl.endsWith(".tar")
                    || normalizedUrl.endsWith(".gz")
                    || normalizedUrl.endsWith(".7z")
                    || normalizedUrl.endsWith(".pdf");
        }

        private <T> Future<T> submit(Callable<T> callable, String actionDescription) {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("Worker " + workerId + " is shut down");
            }
            return executor.submit(callable);
        }

        private <T> T runOnWorker(Callable<T> callable, String actionDescription) {
            try {
                return submit(callable, actionDescription).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to " + actionDescription
                        + " Playwright worker " + workerId, e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Failed to " + actionDescription
                        + " Playwright worker " + workerId, cause);
            }
        }

        private void close() {
            try {
                runOnWorker(() -> {
                    try {
                        if (playwright != null) {
                            playwright.close();
                            playwright = null;
                        }
                    } finally {
                        browser = null;
                    }
                    log.info("Closed Playwright worker={}", workerId);
                    return null;
                }, "close");
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    executor.shutdownNow();
                }
            }
        }
    }

    private static final class PlaywrightThreadFactory implements ThreadFactory {

        private final int workerId;

        private PlaywrightThreadFactory(int workerId) {
            this.workerId = workerId;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "playwright-worker-" + workerId);
            thread.setDaemon(true);
            return thread;
        }
    }
}
