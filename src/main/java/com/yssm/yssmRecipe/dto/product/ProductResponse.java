package com.yssm.yssmRecipe.dto.product;

import java.math.BigDecimal;

public record ProductResponse(
    Long id,
    String productCode,
    String productName,
    Integer safetyStock,
    Integer maxStock,
    String erpUnit,
    boolean active,
    Long recipeId,
    String recipeCode,
    String recipeName,
    String packagingErpUnit,
    BigDecimal gramWeightPerErpUnit,
    String packagingDescription
) {
}
