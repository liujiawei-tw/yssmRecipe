package com.yssm.yssmRecipe.service.recipe;

import java.math.BigDecimal;

public record RecipeMaterialCalculation(
    String materialCode,
    String materialName,
    BigDecimal ratio,
    BigDecimal percentage,
    BigDecimal actualWeightG,
    String formula
) {
}
