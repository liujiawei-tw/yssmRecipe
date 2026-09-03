package com.yssm.yssmRecipe.dto.product;

import java.time.Instant;

public record ProductImportResponse(
    String sourceFileName,
    int importedCount,
    int createdCount,
    int updatedCount,
    Instant importedAt
) {
}
