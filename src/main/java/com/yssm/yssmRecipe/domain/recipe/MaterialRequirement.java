package com.yssm.yssmRecipe.domain.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.PrePersist;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "material_requirement")
public class MaterialRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_plan_id", nullable = false)
    private ProductionPlan productionPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_version_id", nullable = false)
    private RecipeVersion recipeVersion;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal ratio;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal percentage;

    @Column(name = "required_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal requiredWeightG;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MaterialRequirement(ProductionPlan productionPlan, Material material, RecipeVersion recipeVersion, BigDecimal ratio, BigDecimal percentage, BigDecimal requiredWeightG) {
        this.productionPlan = productionPlan;
        this.material = material;
        this.recipeVersion = recipeVersion;
        this.ratio = ratio;
        this.percentage = percentage;
        this.requiredWeightG = requiredWeightG;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
