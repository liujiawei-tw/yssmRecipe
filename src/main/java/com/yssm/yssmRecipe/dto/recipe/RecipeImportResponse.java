package com.yssm.yssmRecipe.dto.recipe;

import java.time.Instant;

public record RecipeImportResponse(
    String sourceFileName,
    int importedCount,
    int createdCount,
    int updatedCount,
    Instant importedAt
) {
}
