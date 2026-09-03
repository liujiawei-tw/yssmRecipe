package com.yssm.yssmRecipe.dto.material;

import jakarta.validation.constraints.NotBlank;

public record MaterialUpsertRequest(
    @NotBlank String materialCode,
    @NotBlank String materialName,
    @NotBlank String baseUnit,
    Boolean active,
    String description
) {
}
