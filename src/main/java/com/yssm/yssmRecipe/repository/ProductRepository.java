package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByProductCode(String productCode);
}
