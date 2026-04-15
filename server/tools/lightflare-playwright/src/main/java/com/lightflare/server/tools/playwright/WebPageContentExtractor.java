package com.lightflare.server.tools.playwright;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;

@RequiredArgsConstructor
public class WebPageContentExtractor {

    private static final String NOISE_SELECTORS = String.join(", ",
            "script",
            "style",
            "noscript",
            "svg",
            "canvas",
            "iframe",
            "header",
            "footer",
            "nav",
            "aside",
            "form",
            "button",
            "input",
            "select",
            "textarea",
            "[role=banner]",
            "[role=navigation]",
            "[role=complementary]",
            "[role=dialog]",
            "[aria-hidden=true]",
            "[hidden]",
            ".cookie",
            ".cookies",
            ".cookie-banner",
            ".consent",
            ".modal",
            ".popup",
            ".newsletter",
            ".advertisement",
            ".ads",
            ".ad",
            ".social",
            ".share",
            ".sidebar",
            ".breadcrumbs",
            ".pagination"
    );

    private static final List<String> MAIN_CONTENT_SELECTORS = List.of(
            "main",
            "article",
            "[role=main]",
            ".main-content",
            ".content",
            ".post-content",
            ".article-content",
            ".entry-content"
    );

    private final PlaywrightService playwrightService;

    public String fetchPageContent(String url) {
        String renderedHtml = playwrightService.fetchPageContent(url);
        return cleanPageContent(renderedHtml, url);
    }

    public String cleanPageContent(String html, String baseUri) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Document document = Jsoup.parse(html, baseUri);
        document.select(NOISE_SELECTORS).remove();

        Element mainContent = selectMainContent(document);
        if (mainContent == null) {
            mainContent = document.body();
        }

        if (mainContent == null) {
            return "";
        }

        return normalizeWhitespace(mainContent.text());
    }

    private Element selectMainContent(Document document) {
        for (String selector : MAIN_CONTENT_SELECTORS) {
            Element candidate = document.selectFirst(selector);
            if (candidate != null && hasMeaningfulText(candidate)) {
                return candidate;
            }
        }

        Elements candidates = document.select("section, div");
        Element bestCandidate = null;
        int bestLength = 0;

        for (Element candidate : candidates) {
            String text = normalizeWhitespace(candidate.text());
            int textLength = text.length();
            if (textLength >= 200 && textLength > bestLength) {
                bestCandidate = candidate;
                bestLength = textLength;
            }
        }

        return bestCandidate;
    }

    private boolean hasMeaningfulText(Element element) {
        return normalizeWhitespace(element.text()).length() >= 200;
    }

    private String normalizeWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
