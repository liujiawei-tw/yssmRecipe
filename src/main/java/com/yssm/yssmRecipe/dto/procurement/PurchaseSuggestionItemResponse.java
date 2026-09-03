package com.yssm.yssmRecipe.dto.procurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseSuggestionItemResponse(
    Long id,
    Long materialId,
    String materialCode,
    String materialName,
    BigDecimal requiredWeightG,
    BigDecimal stockWeightG,
    BigDecimal shortageWeightG,
    BigDecimal purchaseSuggestionWeightG,
    boolean inventoryAvailable,
    Instant inventoryImportedAt,
    String inventorySourceFileName,
    List<PurchaseSuggestionSourceResponse> sources
) {
}
