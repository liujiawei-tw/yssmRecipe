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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_stock_snapshot")
public class ProductStockSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "source_file_name")
    private String sourceFileName;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    public ProductStockSnapshot(Product product, Integer stockQuantity, String sourceFileName) {
        this.product = product;
        this.stockQuantity = stockQuantity;
        this.sourceFileName = sourceFileName;
    }

    @PrePersist
    void onCreate() {
        this.importedAt = Instant.now();
    }
}
