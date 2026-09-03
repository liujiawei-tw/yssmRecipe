package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeVersionItemRepository extends JpaRepository<RecipeVersionItem, Long> {

    @EntityGraph(attributePaths = {"recipeVersion", "recipeVersion.recipe", "material"})
    List<RecipeVersionItem> findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(Long recipeVersionId);

    @Query("""
        select item from RecipeVersionItem item
        join fetch item.recipeVersion version
        join fetch version.recipe recipe
        join fetch item.material material
        where (:recipeId is null or recipe.id = :recipeId)
          and (:recipeVersionId is null or version.id = :recipeVersionId)
          and (:materialId is null or material.id = :materialId)
        order by recipe.recipeCode asc, version.versionDate desc, item.displayOrder asc, item.id asc
        """)
    List<RecipeVersionItem> search(
        @Param("recipeId") Long recipeId,
        @Param("recipeVersionId") Long recipeVersionId,
        @Param("materialId") Long materialId
    );

    long countByRecipeVersionId(Long recipeVersionId);

    void deleteByMaterialId(Long materialId);

    void deleteByRecipeVersionId(Long recipeVersionId);

    void deleteByRecipeVersionRecipeId(Long recipeId);
}
