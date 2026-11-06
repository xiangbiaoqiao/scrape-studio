package com.scrapestudio.project;

import java.util.List;

public record ProjectRunSpec(
        Long id,
        String name,
        String url,
        String itemSelector,
        SelectorType itemSelectorType,
        List<FieldSpec> fields
) {
    public record FieldSpec(
            String name,
            String selector,
            SelectorType selectorType,
            String attribute,
            boolean multiple
    ) {
    }
}
