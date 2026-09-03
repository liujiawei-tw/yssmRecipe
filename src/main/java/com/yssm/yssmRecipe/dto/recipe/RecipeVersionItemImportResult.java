package com.yssm.yssmRecipe.dto.recipe;

public record RecipeVersionItemImportResult(
    int importedVersionCount,
    int importedRowCount
) {
}
