package com.yssm.yssmRecipe.dto.procurement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseSuggestionSourceResponse(
    Long productionPlanId,
    Long materialRequirementId,
    Long productId,
    String productCode,
    String productName,
    Long recipeId,
    String recipeCode,
    String recipeName,
    Long recipeVersionId,
    LocalDate recipeVersionDate,
    Integer plannedQuantity,
    String calculationMode,
    BigDecimal calculationWeightG,
    BigDecimal requiredWeightG
) {
}
