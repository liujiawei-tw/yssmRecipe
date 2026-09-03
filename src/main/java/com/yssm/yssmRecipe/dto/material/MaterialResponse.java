package com.yssm.yssmRecipe.dto.material;

public record MaterialResponse(
    Long id,
    String materialCode,
    String materialName,
    String baseUnit,
    boolean active,
    String description
) {
}
