package com.yssm.yssmRecipe.dto.production;

import java.math.BigDecimal;

public record MaterialRequirementResponse(
    Long materialId,
    String materialCode,
    String materialName,
    Long recipeVersionId,
    BigDecimal ratio,
    BigDecimal percentage,
    BigDecimal requiredWeightG
) {
}
