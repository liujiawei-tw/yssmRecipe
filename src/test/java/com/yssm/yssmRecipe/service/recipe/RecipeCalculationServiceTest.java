package com.yssm.yssmRecipe.service.recipe;

import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import com.yssm.yssmRecipe.repository.RecipeVersionItemRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeCalculationServiceTest {

    @Mock
    private RecipeVersionRepository recipeVersionRepository;

    @Mock
    private RecipeVersionItemRepository recipeVersionItemRepository;

    @InjectMocks
    private RecipeCalculationService recipeCalculationService;

    @Test
    void calculatesRecipeCorrectly() {
        RecipeVersion recipeVersion = buildVersion(
            "錫蘭",
            LocalDate.of(2026, 8, 27),
            "500",
            List.of(
                buildItem("A", "原料A", "1", 1),
                buildItem("B", "原料B", "7", 2),
                buildItem("C", "原料C", "3", 3),
                buildItem("D", "原料D", "4", 4)
            )
        );
        when(recipeVersionItemRepository.findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(recipeVersion.getId()))
            .thenReturn(recipeVersion.getItems());

        RecipeCalculationResult result = recipeCalculationService.calculate(recipeVersion, new BigDecimal("500"));

        assertThat(result.totalRatio()).isEqualByComparingTo("15.000000");
        assertThat(result.totalWeightG()).isEqualByComparingTo("500.000000");
        assertThat(result.items()).hasSize(4);
        assertThat(result.items().get(1).actualWeightG()).isEqualByComparingTo("233.333333");
        assertThat(result.items().get(1).percentage()).isEqualByComparingTo("46.666667");
        assertThat(result.items().stream().map(RecipeMaterialCalculation::actualWeightG).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("500.000000");
    }

    @Test
    void rejectsZeroTotalRatio() {
        RecipeVersion recipeVersion = buildVersion(
            "錫蘭",
            LocalDate.of(2026, 8, 27),
            "500",
            List.of(
                buildItem("A", "原料A", "0", 1),
                buildItem("B", "原料B", "0", 2)
            )
        );
        when(recipeVersionItemRepository.findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(recipeVersion.getId()))
            .thenReturn(recipeVersion.getItems());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> recipeCalculationService.calculate(recipeVersion, new BigDecimal("500")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("totalRatio");
    }

    @Test
    void findsLatestActiveVersion() {
        RecipeVersion recipeVersion = buildVersion(
            "錫蘭",
            LocalDate.of(2026, 8, 27),
            "500",
            List.of(buildItem("A", "原料A", "1", 1))
        );
        when(recipeVersionItemRepository.findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(recipeVersion.getId()))
            .thenReturn(recipeVersion.getItems());
        when(recipeVersionRepository.findTopByRecipeIdAndStatusOrderByVersionDateDescIdDesc(1L, RecipeVersionStatus.ACTIVE))
            .thenReturn(java.util.Optional.of(recipeVersion));

        RecipeCalculationResult result = recipeCalculationService.calculateLatest(1L, new BigDecimal("100"));

        assertThat(result.recipeName()).isEqualTo("錫蘭");
        assertThat(result.items()).hasSize(1);
    }

    private RecipeVersion buildVersion(String recipeName, LocalDate versionDate, String baseWeightG, List<RecipeVersionItem> items) {
        Recipe recipe = new Recipe("R-001", recipeName);
        RecipeVersion recipeVersion = new RecipeVersion(recipe, versionDate, new BigDecimal(baseWeightG), RecipeVersionStatus.ACTIVE);
        recipeVersion.setItems(items);
        items.forEach(item -> item.setRecipeVersion(recipeVersion));
        return recipeVersion;
    }

    private RecipeVersionItem buildItem(String materialCode, String materialName, String ratio, int displayOrder) {
        Material material = new Material(materialCode, materialName, "g");
        return new RecipeVersionItem(null, material, new BigDecimal(ratio), displayOrder);
    }
}
