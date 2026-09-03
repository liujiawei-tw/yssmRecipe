package com.yssm.yssmRecipe;

import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.MaterialInventorySnapshot;
import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.domain.recipe.ProductPackaging;
import com.yssm.yssmRecipe.domain.recipe.ProductRecipeMapping;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import com.yssm.yssmRecipe.repository.MaterialInventorySnapshotRepository;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.ProductPackagingRepository;
import com.yssm.yssmRecipe.repository.ProductRepository;
import com.yssm.yssmRecipe.repository.ProductRecipeMappingRepository;
import com.yssm.yssmRecipe.repository.ProductionPlanRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseSuggestionIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeVersionRepository recipeVersionRepository;

    @Autowired
    private ProductPackagingRepository productPackagingRepository;

    @Autowired
    private ProductRecipeMappingRepository productRecipeMappingRepository;

    @Autowired
    private MaterialInventorySnapshotRepository materialInventorySnapshotRepository;

    @Autowired
    private ProductionPlanRepository productionPlanRepository;

    @Test
    void generatesPurchaseSuggestionFromAggregatedMaterialDemand() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        Material material = materialRepository.save(new Material("MAT-PUR-001", "請購原料", "g"));

        Recipe recipeA = recipeRepository.save(new Recipe("REC-PUR-A", "請購配方A"));
        Recipe recipeB = recipeRepository.save(new Recipe("REC-PUR-B", "請購配方B"));

        RecipeVersion versionA = new RecipeVersion(recipeA, LocalDate.of(2026, 8, 27), new BigDecimal("100"), RecipeVersionStatus.ACTIVE);
        versionA.getItems().add(new RecipeVersionItem(versionA, material, new BigDecimal("1"), 1));
        recipeVersionRepository.save(versionA);

        RecipeVersion versionB = new RecipeVersion(recipeB, LocalDate.of(2026, 8, 27), new BigDecimal("100"), RecipeVersionStatus.ACTIVE);
        versionB.getItems().add(new RecipeVersionItem(versionB, material, new BigDecimal("1"), 1));
        recipeVersionRepository.save(versionB);

        Product productA = productRepository.save(new Product("PRD-PUR-A", "請購商品A", 10, 100, "PCS"));
        Product productB = productRepository.save(new Product("PRD-PUR-B", "請購商品B", 10, 100, "PCS"));

        productPackagingRepository.save(new ProductPackaging(productA, "PCS", new BigDecimal("10"), "10g/pcs"));
        productPackagingRepository.save(new ProductPackaging(productB, "PCS", new BigDecimal("5"), "5g/pcs"));
        productRecipeMappingRepository.save(new ProductRecipeMapping(productA, recipeA, true, true, 1));
        productRecipeMappingRepository.save(new ProductRecipeMapping(productB, recipeB, true, true, 1));

        mockMvc.perform(post("/api/production-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId": %d, "currentStock": 0, "plannedQuantity": 10, "calculationMode": "MANUAL"}
                    """.formatted(productA.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plannedQuantity").value(10));

        mockMvc.perform(post("/api/production-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId": %d, "currentStock": 0, "plannedQuantity": 20, "calculationMode": "MANUAL"}
                    """.formatted(productB.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plannedQuantity").value(20));

        materialInventorySnapshotRepository.save(new MaterialInventorySnapshot(material, new BigDecimal("150"), "material-stock.xlsx"));

        mockMvc.perform(post("/api/purchase-suggestions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.totalRequiredWeightG").value(200))
            .andExpect(jsonPath("$.totalStockWeightG").value(150))
            .andExpect(jsonPath("$.totalShortageWeightG").value(50))
            .andExpect(jsonPath("$.items[0].materialCode").value("MAT-PUR-001"))
            .andExpect(jsonPath("$.items[0].requiredWeightG").value(200))
            .andExpect(jsonPath("$.items[0].stockWeightG").value(150))
            .andExpect(jsonPath("$.items[0].shortageWeightG").value(50))
            .andExpect(jsonPath("$.items[0].purchaseSuggestionWeightG").value(200))
            .andExpect(jsonPath("$.items[0].sources.length()").value(2))
            .andExpect(jsonPath("$.items[0].sources[0].productCode").exists())
            .andExpect(jsonPath("$.items[0].sources[0].recipeCode").exists());

        assertThat(productionPlanRepository.findAll()).hasSize(2);
    }
}
