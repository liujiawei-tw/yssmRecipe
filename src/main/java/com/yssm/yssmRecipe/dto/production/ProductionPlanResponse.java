package com.yssm.yssmRecipe.dto.production;

import com.yssm.yssmRecipe.domain.recipe.CalculationMode;
import java.math.BigDecimal;
import java.util.List;

public record ProductionPlanResponse(
    Long id,
    Long productId,
    String productCode,
    String productName,
    Integer currentStock,
    Integer safetyStock,
    Integer targetStock,
    Integer suggestedQuantity,
    Integer plannedQuantity,
    CalculationMode calculationMode,
    String erpUnit,
    BigDecimal gramWeightPerErpUnit,
    BigDecimal calculationWeightG,
    Long recipeVersionId,
    String recipeName,
    String versionDate,
    List<MaterialRequirementResponse> materialRequirements
) {
}
