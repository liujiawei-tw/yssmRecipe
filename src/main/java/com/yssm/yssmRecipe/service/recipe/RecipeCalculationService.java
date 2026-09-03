package com.yssm.yssmRecipe.service.recipe;

import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionItemRepository;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class RecipeCalculationService {

    private static final int SCALE = 6;

    private final RecipeVersionRepository recipeVersionRepository;
    private final RecipeVersionItemRepository recipeVersionItemRepository;

    @Transactional(readOnly = true)
    public RecipeCalculationResult calculateLatest(@NotNull Long recipeId, @NotNull BigDecimal targetWeightG) {
        RecipeVersion recipeVersion = recipeVersionRepository
            .findTopByRecipeIdAndStatusOrderByVersionDateDescIdDesc(recipeId, RecipeVersionStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("找不到可用的有效配方版本"));
        return calculate(recipeVersion, targetWeightG);
    }

    @Transactional(readOnly = true)
    public RecipeCalculationResult calculateByVersionId(@NotNull Long recipeVersionId, @NotNull BigDecimal targetWeightG) {
        RecipeVersion recipeVersion = recipeVersionRepository.findById(recipeVersionId)
            .orElseThrow(() -> new IllegalArgumentException("找不到指定配方版本"));
        return calculate(recipeVersion, targetWeightG);
    }

    @Transactional(readOnly = true)
    public RecipeCalculationResult calculate(@NotNull RecipeVersion recipeVersion, @NotNull BigDecimal targetWeightG) {
        Assert.notNull(recipeVersion, "recipeVersion must not be null");
        Assert.notNull(targetWeightG, "targetWeightG must not be null");
        if (targetWeightG.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("targetWeightG must be greater than 0");
        }

        List<RecipeVersionItem> sortedItems = recipeVersionItemRepository.findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(recipeVersion.getId()).stream()
            .sorted(Comparator.comparing(item -> item.getDisplayOrder() == null ? Integer.MAX_VALUE : item.getDisplayOrder()))
            .toList();

        if (sortedItems.isEmpty()) {
            throw new IllegalArgumentException("配方沒有任何原料項目");
        }

        BigDecimal totalRatio = sortedItems.stream()
            .map(RecipeVersionItem::getRatio)
            .peek(ratio -> {
                if (ratio == null || ratio.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("ratio 不可小於 0");
                }
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRatio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("totalRatio must be greater than 0");
        }

        List<RecipeMaterialCalculation> items = new ArrayList<>(sortedItems.size());
        BigDecimal allocatedWeight = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        for (int index = 0; index < sortedItems.size(); index++) {
            RecipeVersionItem item = sortedItems.get(index);
            boolean lastItem = index == sortedItems.size() - 1;
            RecipeMaterialCalculation calculation = buildItem(targetWeightG, totalRatio, item, allocatedWeight, lastItem);
            items.add(calculation);
            allocatedWeight = allocatedWeight.add(calculation.actualWeightG()).setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal totalWeightG = items.stream()
            .map(RecipeMaterialCalculation::actualWeightG)
            .reduce(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(SCALE, RoundingMode.HALF_UP);

        return new RecipeCalculationResult(
            recipeVersion.getRecipe().getRecipeName(),
            recipeVersion.getVersionDate(),
            scale(recipeVersion.getBaseWeightG()),
            scale(targetWeightG),
            scale(totalRatio),
            totalWeightG,
            items
        );
    }

    private RecipeMaterialCalculation buildItem(BigDecimal targetWeightG, BigDecimal totalRatio, RecipeVersionItem item, BigDecimal allocatedWeight, boolean lastItem) {
        BigDecimal ratio = scale(item.getRatio());
        BigDecimal percentage = ratio.multiply(BigDecimal.valueOf(100)).divide(totalRatio, SCALE, RoundingMode.HALF_UP);
        BigDecimal actualWeightG = lastItem
            ? targetWeightG.setScale(SCALE, RoundingMode.HALF_UP).subtract(allocatedWeight).setScale(SCALE, RoundingMode.HALF_UP)
            : targetWeightG.multiply(ratio).divide(totalRatio, SCALE, RoundingMode.HALF_UP);
        String formula = formatFormula(targetWeightG, ratio, totalRatio);
        return new RecipeMaterialCalculation(
            item.getMaterial().getMaterialCode(),
            item.getMaterial().getMaterialName(),
            ratio,
            percentage,
            actualWeightG,
            formula
        );
    }

    private String formatFormula(BigDecimal targetWeightG, BigDecimal ratio, BigDecimal totalRatio) {
        return formatNumber(targetWeightG) + " × " + formatNumber(ratio) + " ÷ " + formatNumber(totalRatio);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
