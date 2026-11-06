package com.scrapestudio.project.dto;

import com.scrapestudio.project.Field;

public record FieldDto(
        Long id,
        String name,
        String selector,
        String selectorType,
        String attribute,
        boolean multiple,
        int orderIndex
) {
    public static FieldDto from(Field field) {
        return new FieldDto(
                field.getId(),
                field.getName(),
                field.getSelector(),
                field.getSelectorType().name(),
                field.getAttribute(),
                field.isMultiple(),
                field.getOrderIndex()
        );
    }
}
