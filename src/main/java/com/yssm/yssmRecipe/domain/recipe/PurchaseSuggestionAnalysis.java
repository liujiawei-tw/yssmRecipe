package com.yssm.yssmRecipe.domain.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "purchase_suggestion_analysis")
public class PurchaseSuggestionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "total_required_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalRequiredWeightG;

    @Column(name = "total_stock_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalStockWeightG;

    @Column(name = "total_shortage_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalShortageWeightG;

    @OneToMany(mappedBy = "analysis", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseSuggestionItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.generatedAt = Instant.now();
    }
}
