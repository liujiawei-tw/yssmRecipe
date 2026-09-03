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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.PrePersist;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_recipe_mapping")
public class ProductRecipeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryMapping = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProductRecipeMapping(Product product, Recipe recipe, boolean active, boolean primaryMapping, Integer displayOrder) {
        this.product = product;
        this.recipe = recipe;
        this.active = active;
        this.primaryMapping = primaryMapping;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
