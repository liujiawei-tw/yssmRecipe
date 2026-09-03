package com.yssm.yssmRecipe.domain.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "purchase_suggestion_item")
public class PurchaseSuggestionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private PurchaseSuggestionAnalysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "material_code", nullable = false, length = 64)
    private String materialCode;

    @Column(name = "material_name", nullable = false)
    private String materialName;

    @Column(name = "required_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal requiredWeightG;

    @Column(name = "stock_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal stockWeightG;

    @Column(name = "shortage_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal shortageWeightG;

    @Column(name = "purchase_suggestion_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal purchaseSuggestionWeightG;

    @Column(name = "inventory_available", nullable = false)
    private boolean inventoryAvailable;

    @Column(name = "inventory_imported_at")
    private Instant inventoryImportedAt;

    @Column(name = "inventory_source_file_name")
    private String inventorySourceFileName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "item", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseSuggestionItemSource> sources = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
