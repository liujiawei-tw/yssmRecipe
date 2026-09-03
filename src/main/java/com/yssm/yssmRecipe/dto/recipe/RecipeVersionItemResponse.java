package com.yssm.yssmRecipe.dto.recipe;

import java.math.BigDecimal;

public record RecipeVersionItemResponse(
    Long id,
    Long materialId,
    String materialCode,
    String materialName,
    BigDecimal ratio,
    Integer displayOrder
) {
}
