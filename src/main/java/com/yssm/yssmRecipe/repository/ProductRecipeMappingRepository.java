package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.ProductRecipeMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRecipeMappingRepository extends JpaRepository<ProductRecipeMapping, Long> {
    List<ProductRecipeMapping> findByProductIdOrderByDisplayOrderAscIdAsc(Long productId);
    Optional<ProductRecipeMapping> findFirstByProductIdAndActiveTrueAndPrimaryMappingTrueOrderByDisplayOrderAscIdAsc(Long productId);

    void deleteByProductId(Long productId);

    void deleteByRecipeId(Long recipeId);
}
