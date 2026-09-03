package com.yssm.yssmRecipe.service.recipe;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecipeCalculationResult(
    String recipeName,
    LocalDate versionDate,
    BigDecimal baseWeightG,
    BigDecimal targetWeightG,
    BigDecimal totalRatio,
    BigDecimal totalWeightG,
    List<RecipeMaterialCalculation> items
) {
}
