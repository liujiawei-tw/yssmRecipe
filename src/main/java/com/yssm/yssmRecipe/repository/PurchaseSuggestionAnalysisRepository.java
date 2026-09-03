package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.PurchaseSuggestionAnalysis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseSuggestionAnalysisRepository extends JpaRepository<PurchaseSuggestionAnalysis, Long> {
    Optional<PurchaseSuggestionAnalysis> findTopByOrderByGeneratedAtDescIdDesc();

    @Query("""
        select distinct analysis
        from PurchaseSuggestionAnalysis analysis
        left join fetch analysis.items item
        left join fetch item.material
        order by analysis.generatedAt desc, analysis.id desc
        """)
    List<PurchaseSuggestionAnalysis> findAllWithDetailsOrderByGeneratedAtDescIdDesc();

    @Query("""
        select distinct analysis
        from PurchaseSuggestionAnalysis analysis
        left join fetch analysis.items item
        left join fetch item.material
        where analysis.id = :id
        """)
    Optional<PurchaseSuggestionAnalysis> findByIdWithDetails(@Param("id") Long id);
}
