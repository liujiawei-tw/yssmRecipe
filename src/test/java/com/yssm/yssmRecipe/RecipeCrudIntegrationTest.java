package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.repository.RecipeRepository;
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

class RecipeCrudIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void recipeCrudShouldWork() throws Exception {
        createRecipe("""
            {
              "recipeCode": "REC-901",
              "recipeName": "測試配方",
              "active": true,
              "description": "create"
            }
            """);

        Long recipeId = recipeRepository.findByRecipeCode("REC-901").orElseThrow().getId();

        mockMvc.perform(get("/api/recipes/{id}", recipeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipeCode").value("REC-901"))
            .andExpect(jsonPath("$.recipeName").value("測試配方"))
            .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(put("/api/recipes/{id}", recipeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "recipeCode": "REC-901A",
                      "recipeName": "測試配方-更新",
                      "active": false,
                      "description": "updated"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipeCode").value("REC-901A"))
            .andExpect(jsonPath("$.recipeName").value("測試配方-更新"))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.description").value("updated"));

        mockMvc.perform(delete("/api/recipes/{id}", recipeId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/recipes/{id}", recipeId))
            .andExpect(status().isNotFound());
    }

    @Test
    void recipeListShouldContainCreatedRecipe() throws Exception {
        recipeRepository.save(new Recipe("REC-902", "列表配方"));

        mockMvc.perform(get("/api/recipes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.recipeCode=='REC-902')]").exists());
    }

    private void createRecipe(String body) throws Exception {
        mockMvc.perform(post("/api/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists());
    }
}
