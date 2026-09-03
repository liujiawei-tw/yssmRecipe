package com.yssm.yssmRecipe.dto.procurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseSuggestionResponse(
    Long id,
    Instant generatedAt,
    BigDecimal totalRequiredWeightG,
    BigDecimal totalStockWeightG,
    BigDecimal totalShortageWeightG,
    List<PurchaseSuggestionItemResponse> items
) {
}
