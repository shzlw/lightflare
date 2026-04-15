package com.lightflare.server.tools.bravesearch;

import java.util.List;

record BraveSearchOutput(
        String query,
        String alteredQuery,
        Boolean moreResultsAvailable,
        List<Result> results
) {

    record Result(
            String title,
            String url,
            String description,
            String pageAge,
            List<String> extraSnippets
    ) {
    }
}
