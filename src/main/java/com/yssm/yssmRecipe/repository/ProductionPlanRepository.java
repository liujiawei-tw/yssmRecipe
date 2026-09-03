package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.ProductionPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {

    @Query("""
        select distinct plan
        from ProductionPlan plan
        left join fetch plan.product
        left join fetch plan.recipeVersion recipeVersion
        left join fetch recipeVersion.recipe
        left join fetch plan.materialRequirements requirement
        left join fetch requirement.material
        left join fetch requirement.recipeVersion
        order by plan.createdAt desc, plan.id desc
        """)
    List<ProductionPlan> findAllWithDetailsOrderByCreatedAtDescIdDesc();

    Optional<ProductionPlan> findFirstByPlanningBatchKeyIsNotNullOrderByCreatedAtDescIdDesc();

    @Query("""
        select distinct plan
        from ProductionPlan plan
        left join fetch plan.product
        left join fetch plan.recipeVersion recipeVersion
        left join fetch recipeVersion.recipe
        left join fetch plan.materialRequirements requirement
        left join fetch requirement.material
        left join fetch requirement.recipeVersion
        where plan.planningBatchKey = :planningBatchKey
        order by plan.createdAt desc, plan.id desc
        """)
    List<ProductionPlan> findByPlanningBatchKeyWithDetails(@Param("planningBatchKey") String planningBatchKey);

    void deleteByRecipeVersionId(Long recipeVersionId);

    void deleteByRecipeVersionRecipeId(Long recipeId);

    void deleteByProductId(Long productId);
}
