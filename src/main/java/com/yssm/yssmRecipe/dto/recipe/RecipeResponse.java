package com.yssm.yssmRecipe.dto.recipe;

public record RecipeResponse(
    Long id,
    String recipeCode,
    String recipeName,
    boolean active,
    String description
) {
}
