package com.yssm.yssmRecipe.dto.material;

import java.time.Instant;

public record MaterialImportResponse(
    String sourceFileName,
    int importedCount,
    int createdCount,
    int updatedCount,
    Instant importedAt
) {
}
