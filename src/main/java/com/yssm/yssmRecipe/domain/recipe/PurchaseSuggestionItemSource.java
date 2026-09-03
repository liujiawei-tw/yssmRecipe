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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "purchase_suggestion_item_source")
public class PurchaseSuggestionItemSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private PurchaseSuggestionItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_plan_id", nullable = false)
    private ProductionPlan productionPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_requirement_id", nullable = false)
    private MaterialRequirement materialRequirement;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "recipe_code", nullable = false, length = 64)
    private String recipeCode;

    @Column(name = "recipe_name", nullable = false)
    private String recipeName;

    @Column(name = "recipe_version_id", nullable = false)
    private Long recipeVersionId;

    @Column(name = "recipe_version_date", nullable = false)
    private LocalDate recipeVersionDate;

    @Column(name = "planned_quantity", nullable = false)
    private Integer plannedQuantity;

    @Column(name = "calculation_mode", nullable = false, length = 16)
    private String calculationMode;

    @Column(name = "calculation_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal calculationWeightG;

    @Column(name = "required_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal requiredWeightG;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PurchaseSuggestionItemSource(PurchaseSuggestionItem item, ProductionPlan productionPlan, MaterialRequirement materialRequirement) {
        this.item = item;
        this.productionPlan = productionPlan;
        this.materialRequirement = materialRequirement;
        this.productId = productionPlan.getProduct().getId();
        this.productCode = productionPlan.getProduct().getProductCode();
        this.productName = productionPlan.getProduct().getProductName();
        this.recipeId = productionPlan.getRecipeVersion().getRecipe().getId();
        this.recipeCode = productionPlan.getRecipeVersion().getRecipe().getRecipeCode();
        this.recipeName = productionPlan.getRecipeVersion().getRecipe().getRecipeName();
        this.recipeVersionId = productionPlan.getRecipeVersion().getId();
        this.recipeVersionDate = productionPlan.getRecipeVersion().getVersionDate();
        this.plannedQuantity = productionPlan.getPlannedQuantity();
        this.calculationMode = productionPlan.getCalculationMode().name();
        this.calculationWeightG = productionPlan.getCalculationWeightG();
        this.requiredWeightG = materialRequirement.getRequiredWeightG();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
