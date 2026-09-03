package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.MaterialRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRequirementRepository extends JpaRepository<MaterialRequirement, Long> {
    void deleteByMaterialId(Long materialId);

    void deleteByRecipeVersionId(Long recipeVersionId);

    void deleteByRecipeVersionRecipeId(Long recipeId);

    void deleteByProductionPlanProductId(Long productId);
}
