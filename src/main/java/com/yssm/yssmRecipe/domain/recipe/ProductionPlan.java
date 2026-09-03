package com.yssm.yssmRecipe.domain.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.PrePersist;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "production_plan")
public class ProductionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    @Column(name = "target_stock", nullable = false)
    private Integer targetStock;

    @Column(name = "suggested_quantity", nullable = false)
    private Integer suggestedQuantity;

    @Column(name = "planned_quantity", nullable = false)
    private Integer plannedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_mode", nullable = false, length = 16)
    private CalculationMode calculationMode;

    @Column(name = "gram_weight_per_erp_unit", nullable = false, precision = 18, scale = 6)
    private BigDecimal gramWeightPerErpUnit;

    @Column(name = "calculation_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal calculationWeightG;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_version_id", nullable = false)
    private RecipeVersion recipeVersion;

    @Column(nullable = false, length = 32)
    private String status = "CALCULATED";

    @Column(name = "planning_batch_key", length = 64)
    private String planningBatchKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "productionPlan", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<MaterialRequirement> materialRequirements = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
