package com.kingsfarm.kingsfarmbackend.feedmill.dto;

public record RequirementRow(
        Long ingredientId,
        String ingredientName,
        double needed,
        double available,
        String unit,
        boolean ok
) {
}
