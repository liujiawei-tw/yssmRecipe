package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeVersionBatchCrudIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    MaterialRepository materialRepository;

    @Autowired
    RecipeVersionRepository recipeVersionRepository;

    @Autowired
    WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void shouldCreateUpdateAndReadWholeRecipeVersionBatch() throws Exception {
        Recipe recipe = recipeRepository.save(new Recipe("REC-BATCH-001", "批次測試配方"));
        Material materialA = materialRepository.save(new Material("MAT-BATCH-A", "批次原料A", "g"));
        Material materialB = materialRepository.save(new Material("MAT-BATCH-B", "批次原料B", "g"));
        Material materialC = materialRepository.save(new Material("MAT-BATCH-C", "批次原料C", "g"));

        mockMvc.perform(post("/api/recipes/{recipeId}/versions", recipe.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionDate": "2026-08-27",
                      "baseWeightG": 1000,
                      "status": "ACTIVE",
                      "createdBy": "tester",
                      "items": [
                        { "materialId": %d, "ratio": 1.0, "displayOrder": 1 },
                        { "materialId": %d, "ratio": 2.0, "displayOrder": 2 }
                      ]
                    }
                    """.formatted(materialA.getId(), materialB.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].materialCode").value("MAT-BATCH-A"))
            .andExpect(jsonPath("$.items[1].materialCode").value("MAT-BATCH-B"));

        Long versionId = recipeVersionRepository.findByRecipeIdOrderByVersionDateDescIdDesc(recipe.getId()).getFirst().getId();

        mockMvc.perform(put("/api/recipe-versions/{id}", versionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionDate": "2026-08-28",
                      "baseWeightG": 1200,
                      "status": "ACTIVE",
                      "createdBy": "tester2",
                      "items": [
                        { "materialId": %d, "ratio": 1.5, "displayOrder": 1 },
                        { "materialId": %d, "ratio": 3.5, "displayOrder": 2 }
                      ]
                    }
                    """.formatted(materialA.getId(), materialC.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versionDate").value("2026-08-28"))
            .andExpect(jsonPath("$.baseWeightG").value(1200))
            .andExpect(jsonPath("$.createdBy").value("tester2"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].materialCode").value("MAT-BATCH-A"))
            .andExpect(jsonPath("$.items[1].materialCode").value("MAT-BATCH-C"))
            .andExpect(jsonPath("$.items[1].ratio").value(3.5));

        mockMvc.perform(get("/api/recipe-versions/{id}", versionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].materialCode").value("MAT-BATCH-A"))
            .andExpect(jsonPath("$.items[1].materialCode").value("MAT-BATCH-C"));
    }
}
