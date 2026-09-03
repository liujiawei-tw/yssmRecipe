package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.PurchaseSuggestionItemSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseSuggestionItemSourceRepository extends JpaRepository<PurchaseSuggestionItemSource, Long> {
    void deleteByProductId(Long productId);

    void deleteByProductionPlanRecipeVersionRecipeId(Long recipeId);

    void deleteByItemMaterialId(Long materialId);

    void deleteByMaterialRequirementMaterialId(Long materialId);
}
