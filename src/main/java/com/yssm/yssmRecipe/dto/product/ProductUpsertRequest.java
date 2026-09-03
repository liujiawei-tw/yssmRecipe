package com.yssm.yssmRecipe.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductUpsertRequest(
    @NotBlank String productCode,
    @NotBlank String productName,
    @NotNull Integer safetyStock,
    @NotNull Integer maxStock,
    @NotBlank String erpUnit,
    Boolean active,
    Long recipeId,
    @NotBlank String packagingErpUnit,
    @NotNull @DecimalMin(value = "0.000001", inclusive = true) BigDecimal gramWeightPerErpUnit,
    String packagingDescription
) {
}
