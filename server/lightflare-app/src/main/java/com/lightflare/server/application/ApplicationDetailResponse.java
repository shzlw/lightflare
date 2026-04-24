package com.lightflare.server.application;

import java.util.List;
import lombok.Builder;

@Builder
public record ApplicationDetailResponse(
        ApplicationResponse application,
        List<ApplicationVersionResponse> versions,
        List<ApplicationRunResponse> recentRuns
) {
}
