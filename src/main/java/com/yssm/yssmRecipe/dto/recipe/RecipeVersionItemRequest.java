package com.yssm.yssmRecipe.dto.recipe;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RecipeVersionItemRequest(
    @NotNull Long materialId,
    @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal ratio,
    @NotNull Integer displayOrder
) {
}
