package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeVersionFirstVersionIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    MaterialRepository materialRepository;

    @Autowired
    WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void shouldCreateFirstRecipeVersionWithInitialItem() throws Exception {
        Recipe recipe = recipeRepository.save(new Recipe("REC-FIRST-001", "首版測試配方"));
        Material material = materialRepository.save(new Material("MAT-FIRST-001", "首版測試原料", "g"));

        mockMvc.perform(post("/api/recipes/{recipeId}/versions", recipe.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionDate": "2026-08-27",
                      "baseWeightG": 500,
                      "status": "ACTIVE",
                      "createdBy": "tester",
                      "items": [
                        {
                          "materialId": %d,
                          "ratio": 1.5,
                          "displayOrder": 1
                        }
                      ]
                    }
                    """.formatted(material.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipeId").value(recipe.getId().intValue()))
            .andExpect(jsonPath("$.recipeCode").value("REC-FIRST-001"))
            .andExpect(jsonPath("$.recipeName").value("首版測試配方"))
            .andExpect(jsonPath("$.versionDate").value("2026-08-27"))
            .andExpect(jsonPath("$.baseWeightG").value(500))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdBy").value("tester"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].materialId").value(material.getId().intValue()))
            .andExpect(jsonPath("$.items[0].materialCode").value("MAT-FIRST-001"))
            .andExpect(jsonPath("$.items[0].materialName").value("首版測試原料"))
            .andExpect(jsonPath("$.items[0].ratio").value(1.5))
            .andExpect(jsonPath("$.items[0].displayOrder").value(1));

        mockMvc.perform(get("/api/recipes/{recipeId}/versions", recipe.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].recipeCode").value("REC-FIRST-001"));

        mockMvc.perform(get("/api/recipes/{recipeId}/versions/latest", recipe.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipeCode").value("REC-FIRST-001"))
            .andExpect(jsonPath("$.items.length()").value(1));
    }
}
