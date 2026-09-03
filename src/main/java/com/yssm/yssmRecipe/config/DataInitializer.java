package com.yssm.yssmRecipe.config;

import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.domain.recipe.ProductPackaging;
import com.yssm.yssmRecipe.domain.recipe.ProductRecipeMapping;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.ProductPackagingRepository;
import com.yssm.yssmRecipe.repository.ProductRecipeMappingRepository;
import com.yssm.yssmRecipe.repository.ProductRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MaterialRepository materialRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final ProductRepository productRepository;
    private final ProductPackagingRepository productPackagingRepository;
    private final ProductRecipeMappingRepository productRecipeMappingRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (recipeRepository.count() > 0 || productRepository.count() > 0 || materialRepository.count() > 0) {
            return;
        }

        List<Material> materials = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            materials.add(materialRepository.save(new Material(
                String.format("MAT-%03d", index),
                String.format("測試原料 %02d", index),
                "g"
            )));
        }

        List<Recipe> recipes = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            recipes.add(recipeRepository.save(new Recipe(
                String.format("REC-%03d", index),
                String.format("測試配方 %02d", index)
            )));
        }

        for (int index = 0; index < recipes.size(); index++) {
            Recipe recipe = recipes.get(index);
            Product product = productRepository.save(new Product(
                String.format("PRD-%03d", index + 1),
                String.format("測試商品 %02d", index + 1),
                20 + index,
                120 + (index * 5),
                index % 2 == 0 ? "箱" : "袋"
            ));

            productPackagingRepository.save(new ProductPackaging(
                product,
                product.getErpUnit(),
                new BigDecimal(index % 2 == 0 ? "2000" : "1000"),
                index % 2 == 0 ? "50g * 40包/箱" : "25g * 40包/袋"
            ));
            productRecipeMappingRepository.save(new ProductRecipeMapping(product, recipe, true, true, 1));

            LocalDate archivedVersionDate = LocalDate.of(2026, 8, 1).plusDays(index);
            LocalDate activeVersionDate = LocalDate.of(2026, 8, 13).plusDays(index);
            saveVersion(
                recipe,
                archivedVersionDate,
                new BigDecimal("500"),
                RecipeVersionStatus.ARCHIVED,
                buildItems(materials, index, 1)
            );
            saveVersion(
                recipe,
                activeVersionDate,
                new BigDecimal("500"),
                RecipeVersionStatus.ACTIVE,
                buildItems(materials, index, 2)
            );
        }
    }

    private RecipeVersion saveVersion(Recipe recipe, LocalDate versionDate, BigDecimal baseWeightG, RecipeVersionStatus status, List<RecipeVersionItem> items) {
        RecipeVersion version = new RecipeVersion(recipe, versionDate, baseWeightG, status);
        items.forEach(item -> item.setRecipeVersion(version));
        version.getItems().addAll(items);
        return recipeVersionRepository.save(version);
    }

    private RecipeVersionItem item(Material material, String ratio, int displayOrder) {
        return new RecipeVersionItem(null, material, new BigDecimal(ratio), displayOrder);
    }

    private List<RecipeVersionItem> buildItems(List<Material> materials, int recipeIndex, int ratioOffset) {
        int start = recipeIndex % materials.size();
        List<RecipeVersionItem> items = new ArrayList<>();
        for (int offset = 0; offset < 4; offset++) {
            Material material = materials.get((start + offset) % materials.size());
            int ratio = ratioOffset + offset;
            items.add(item(material, String.valueOf(ratio), offset + 1));
        }
        return items;
    }
}
