package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, Long> {

    @EntityGraph(attributePaths = {"recipe", "items", "items.material"})
    Optional<RecipeVersion> findTopByRecipeIdAndStatusOrderByVersionDateDescIdDesc(Long recipeId, RecipeVersionStatus status);

    @EntityGraph(attributePaths = {"recipe", "items", "items.material"})
    Optional<RecipeVersion> findById(Long id);

    @EntityGraph(attributePaths = {"recipe", "items", "items.material"})
    Optional<RecipeVersion> findByRecipeIdAndVersionDate(Long recipeId, LocalDate versionDate);

    @EntityGraph(attributePaths = {"recipe", "items", "items.material"})
    List<RecipeVersion> findByRecipeIdOrderByVersionDateDescIdDesc(Long recipeId);

    @EntityGraph(attributePaths = {"recipe", "items", "items.material"})
    List<RecipeVersion> findAllByOrderByRecipeIdAscVersionDateDescIdDesc();
}
