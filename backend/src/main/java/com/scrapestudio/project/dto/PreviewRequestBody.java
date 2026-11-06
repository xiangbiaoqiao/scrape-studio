package com.scrapestudio.project.dto;

import com.scrapestudio.worker.dto.PreviewRequest;

import java.util.List;

public record PreviewRequestBody(
        String url,
        String itemSelector,
        String itemSelectorType,
        List<PreviewRequest.PreviewField> fields,
        Integer limit
) {
}
