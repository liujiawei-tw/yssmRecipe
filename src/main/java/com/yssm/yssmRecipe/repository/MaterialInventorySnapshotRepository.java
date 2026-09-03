package com.yssm.yssmRecipe.repository;

import com.yssm.yssmRecipe.domain.recipe.MaterialInventorySnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialInventorySnapshotRepository extends JpaRepository<MaterialInventorySnapshot, Long> {
    Optional<MaterialInventorySnapshot> findTopByMaterialIdOrderByImportedAtDescIdDesc(Long materialId);

    void deleteByMaterialId(Long materialId);
}
