package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.repository.ProductRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductCrudIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void productCrudShouldWork() throws Exception {
        Recipe recipe = recipeRepository.save(new Recipe("REC-PROD-001", "商品測試配方"));

        createProduct("""
            {
              "productCode": "PRD-901",
              "productName": "測試商品",
              "safetyStock": 10,
              "maxStock": 100,
              "erpUnit": "PCS",
              "active": true,
              "recipeId": %d,
              "packagingErpUnit": "BOX",
              "gramWeightPerErpUnit": 12.5,
              "packagingDescription": "測試包裝"
            }
            """.formatted(recipe.getId()));

        Long productId = productRepository.findByProductCode("PRD-901").orElseThrow().getId();

        mockMvc.perform(get("/api/products/{id}", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productCode").value("PRD-901"))
            .andExpect(jsonPath("$.productName").value("測試商品"))
            .andExpect(jsonPath("$.safetyStock").value(10))
            .andExpect(jsonPath("$.maxStock").value(100))
            .andExpect(jsonPath("$.erpUnit").value("PCS"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.recipeId").value(recipe.getId().intValue()))
            .andExpect(jsonPath("$.recipeCode").value("REC-PROD-001"))
            .andExpect(jsonPath("$.recipeName").value("商品測試配方"))
            .andExpect(jsonPath("$.packagingErpUnit").value("BOX"))
            .andExpect(jsonPath("$.gramWeightPerErpUnit").value(12.5))
            .andExpect(jsonPath("$.packagingDescription").value("測試包裝"));

        mockMvc.perform(put("/api/products/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "productCode": "PRD-901A",
                      "productName": "測試商品-更新",
                      "safetyStock": 20,
                      "maxStock": 200,
                      "erpUnit": "PCS",
                      "active": false,
                      "recipeId": null,
                      "packagingErpUnit": "PACK",
                      "gramWeightPerErpUnit": 25,
                      "packagingDescription": "更新包裝"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productCode").value("PRD-901A"))
            .andExpect(jsonPath("$.productName").value("測試商品-更新"))
            .andExpect(jsonPath("$.safetyStock").value(20))
            .andExpect(jsonPath("$.maxStock").value(200))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.recipeId").doesNotExist())
            .andExpect(jsonPath("$.packagingErpUnit").value("PACK"))
            .andExpect(jsonPath("$.gramWeightPerErpUnit").value(25))
            .andExpect(jsonPath("$.packagingDescription").value("更新包裝"));

        mockMvc.perform(delete("/api/products/{id}", productId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.productCode").value("PRD-901A"));
    }

    @Test
    void productListShouldContainCreatedProduct() throws Exception {
        productRepository.save(new Product("PRD-902", "列表商品", 5, 50, "PCS"));

        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.productCode=='PRD-902')]").exists());
    }

    private void createProduct(String body) throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists());
    }
}
