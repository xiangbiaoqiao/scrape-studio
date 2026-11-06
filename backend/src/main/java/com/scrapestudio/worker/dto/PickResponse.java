package com.scrapestudio.worker.dto;

import java.util.List;

public record PickResponse(
        String itemSelector,
        String itemSelectorType,
        int itemMatchCount,
        List<SelectorCandidate> fieldSelectors,
        ElementInfo element,
        String itemPreview
) {
    public record SelectorCandidate(
            String selector,
            String type,
            int matchCount,
            String sample
    ) {
    }

    public record ElementInfo(
            String tag,
            String text,
            String html,
            String preview
    ) {
    }
}
