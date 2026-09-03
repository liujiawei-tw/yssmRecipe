package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.Recipe;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    Optional<Recipe> findByRecipeCode(String recipeCode);
}
