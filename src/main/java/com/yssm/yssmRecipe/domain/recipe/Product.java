package com.yssm.yssmRecipe.domain.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true, length = 64)
    private String productCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    @Column(name = "max_stock", nullable = false)
    private Integer maxStock;

    @Column(name = "erp_unit", nullable = false, length = 32)
    private String erpUnit;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product")
    private List<ProductPackaging> packagings = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    private List<ProductRecipeMapping> recipeMappings = new ArrayList<>();

    public Product(String productCode, String productName, Integer safetyStock, Integer maxStock, String erpUnit) {
        this.productCode = productCode;
        this.productName = productName;
        this.safetyStock = safetyStock;
        this.maxStock = maxStock;
        this.erpUnit = erpUnit;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
