package com.yssm.yssmRecipe.domain.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
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
import java.time.LocalDate;
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
@Table(name = "recipe_version")
public class RecipeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "version_date", nullable = false)
    private LocalDate versionDate;

    @Column(name = "base_weight_g", nullable = false, precision = 18, scale = 6)
    private BigDecimal baseWeightG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecipeVersionStatus status = RecipeVersionStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @OneToMany(mappedBy = "recipeVersion", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeVersionItem> items = new ArrayList<>();

    public RecipeVersion(Recipe recipe, LocalDate versionDate, BigDecimal baseWeightG, RecipeVersionStatus status) {
        this.recipe = recipe;
        this.versionDate = versionDate;
        this.baseWeightG = baseWeightG;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
