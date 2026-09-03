package com.yssm.yssmRecipe.dto.production;

import com.yssm.yssmRecipe.domain.recipe.CalculationMode;
import jakarta.validation.constraints.NotNull;

public record ProductionPlanRequest(
    @NotNull Long productId,
    Integer currentStock,
    Integer plannedQuantity,
    CalculationMode calculationMode
) {
}
