package com.yssm.yssmRecipe.dto.inventory;

import java.time.Instant;

public record InventoryImportResponse(
    String sourceFileName,
    String inventoryType,
    int importedCount,
    Instant importedAt
) {
}
