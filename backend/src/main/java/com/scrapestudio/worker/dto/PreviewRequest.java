package com.scrapestudio.worker.dto;

import java.util.List;

public record PreviewRequest(
        String url,
        String itemSelector,
        String itemSelectorType,
        List<PreviewField> fields,
        int limit
) {
    public record PreviewField(
            String name,
            String selector,
            String selectorType,
            String attribute,
            boolean multiple
    ) {
    }
}
