package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionItemRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeVersionItemCrudIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    MaterialRepository materialRepository;

    @Autowired
    RecipeVersionRepository recipeVersionRepository;

    @Autowired
    RecipeVersionItemRepository recipeVersionItemRepository;

    @Autowired
    WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private Long recipeVersionId;
    private Long materialCId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        Recipe recipe = recipeRepository.save(new Recipe("REC-ITEM-001", "比例測試配方"));
        Material materialA = materialRepository.save(new Material("MAT-A", "原料A", "g"));
        Material materialB = materialRepository.save(new Material("MAT-B", "原料B", "g"));
        Material materialC = materialRepository.save(new Material("MAT-C", "原料C", "g"));

        RecipeVersion version = new RecipeVersion(recipe, LocalDate.of(2026, 8, 27), new BigDecimal("500"), RecipeVersionStatus.ACTIVE);
        version.getItems().add(new RecipeVersionItem(version, materialA, new BigDecimal("1"), 1));
        version.getItems().add(new RecipeVersionItem(version, materialB, new BigDecimal("2"), 2));
        RecipeVersion savedVersion = recipeVersionRepository.save(version);

        recipeVersionId = savedVersion.getId();
        materialCId = materialC.getId();
    }

    @Test
    void recipeVersionItemCrudShouldWork() throws Exception {
        createItem("""
            {
              "recipeVersionId": %d,
              "materialId": %d,
              "ratio": 3,
              "displayOrder": 3
            }
            """.formatted(recipeVersionId, materialCId));

        Long createdId = recipeVersionItemRepository
            .search(null, recipeVersionId, materialCId)
            .stream()
            .findFirst()
            .orElseThrow()
            .getId();

        mockMvc.perform(get("/api/recipe-version-items/{id}", createdId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipeVersionId").value(recipeVersionId.intValue()))
            .andExpect(jsonPath("$.materialId").value(materialCId.intValue()))
            .andExpect(jsonPath("$.ratio").value(3))
            .andExpect(jsonPath("$.displayOrder").value(3));

        mockMvc.perform(get("/api/recipe-version-items")
                .param("recipeVersionId", String.valueOf(recipeVersionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(put("/api/recipe-version-items/{id}", createdId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "recipeVersionId": %d,
                      "materialId": %d,
                      "ratio": 4,
                      "displayOrder": 3
                    }
                    """.formatted(recipeVersionId, materialCId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ratio").value(4))
            .andExpect(jsonPath("$.displayOrder").value(3));

        mockMvc.perform(delete("/api/recipe-version-items/{id}", createdId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/recipe-version-items")
                .param("recipeVersionId", String.valueOf(recipeVersionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void recipeVersionItemListShouldFilterByMaterial() throws Exception {
        mockMvc.perform(get("/api/recipe-version-items")
                .param("recipeVersionId", String.valueOf(recipeVersionId))
                .param("materialId", String.valueOf(materialCId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    private void createItem(String body) throws Exception {
        mockMvc.perform(post("/api/recipe-version-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists());
    }
}
