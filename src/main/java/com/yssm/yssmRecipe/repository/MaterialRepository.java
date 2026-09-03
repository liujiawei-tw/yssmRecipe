package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.Material;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    Optional<Material> findByMaterialCode(String materialCode);
}
