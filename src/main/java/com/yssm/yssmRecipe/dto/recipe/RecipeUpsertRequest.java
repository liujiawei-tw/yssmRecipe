package com.yssm.yssmRecipe.dto.recipe;

import jakarta.validation.constraints.NotBlank;

public record RecipeUpsertRequest(
    @NotBlank String recipeCode,
    @NotBlank String recipeName,
    Boolean active,
    String description
) {
}
