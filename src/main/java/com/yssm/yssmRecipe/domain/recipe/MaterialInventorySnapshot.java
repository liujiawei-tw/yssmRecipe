package com.yssm.yssmRecipe.domain.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "material_inventory_snapshot")
public class MaterialInventorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "stock_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal stockQuantity;

    @Column(name = "source_file_name")
    private String sourceFileName;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    public MaterialInventorySnapshot(Material material, BigDecimal stockQuantity, String sourceFileName) {
        this.material = material;
        this.stockQuantity = stockQuantity;
        this.sourceFileName = sourceFileName;
    }

    @PrePersist
    void onCreate() {
        this.importedAt = Instant.now();
    }
}
