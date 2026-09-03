package com.yssm.yssmRecipe.dto.recipe;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecipeVersionResponse(
    Long id,
    Long recipeId,
    String recipeCode,
    String recipeName,
    LocalDate versionDate,
    BigDecimal baseWeightG,
    String status,
    String createdBy,
    List<RecipeVersionItemResponse> items
) {
}
