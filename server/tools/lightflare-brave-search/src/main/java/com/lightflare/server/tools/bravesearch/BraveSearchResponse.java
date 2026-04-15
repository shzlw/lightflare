package com.lightflare.server.tools.bravesearch;

import java.util.List;

record BraveSearchResponse(
        String type,
        Query query,
        Web web
) {

    record Query(
            String original,
            String altered,
            Boolean more_results_available
    ) {
    }

    record Web(
            String type,
            List<Result> results
    ) {
    }

    record Result(
            String title,
            String url,
            String description,
            String page_age,
            List<String> extra_snippets
    ) {
    }
}
