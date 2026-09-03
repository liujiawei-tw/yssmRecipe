package com.yssm.yssmRecipe.service.recipe;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RecipeCalculationRequest(
    Long recipeId,
    Long recipeVersionId,
    @NotNull @DecimalMin(value = "0.000001", inclusive = true) BigDecimal targetWeightG
) {
}
