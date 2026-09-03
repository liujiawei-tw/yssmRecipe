package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.ProductPackaging;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPackagingRepository extends JpaRepository<ProductPackaging, Long> {
    Optional<ProductPackaging> findFirstByProductIdAndActiveTrueOrderByIdDesc(Long productId);

    void deleteByProductId(Long productId);
}
