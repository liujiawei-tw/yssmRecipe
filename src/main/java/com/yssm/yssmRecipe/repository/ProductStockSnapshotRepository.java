package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.ProductStockSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockSnapshotRepository extends JpaRepository<ProductStockSnapshot, Long> {
    Optional<ProductStockSnapshot> findTopByProductIdOrderByImportedAtDescIdDesc(Long productId);

    void deleteByProductId(Long productId);
}
