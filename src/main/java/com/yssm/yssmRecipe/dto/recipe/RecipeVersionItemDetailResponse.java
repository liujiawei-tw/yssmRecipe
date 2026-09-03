package com.yssm.yssmRecipe.dto.recipe;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.io.Serializable;

public record RecipeVersionItemDetailResponse(
    Long id,
    Long recipeVersionId,
    Long recipeId,
    String recipeCode,
    String recipeName,
    LocalDate versionDate,
    String versionStatus,
    BigDecimal baseWeightG,
    Long materialId,
    String materialCode,
    String materialName,
    BigDecimal ratio,
    Integer displayOrder,
    Instant createdAt
) implements Serializable {
}
