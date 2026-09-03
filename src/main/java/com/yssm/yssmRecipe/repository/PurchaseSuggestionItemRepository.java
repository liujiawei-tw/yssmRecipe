package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.PurchaseSuggestionItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseSuggestionItemRepository extends JpaRepository<PurchaseSuggestionItem, Long> {
    void deleteByMaterialId(Long materialId);
}
