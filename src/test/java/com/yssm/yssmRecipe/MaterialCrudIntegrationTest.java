package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.repository.MaterialRepository;
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

class MaterialCrudIntegrationTest extends AbstractH2IntegrationTest {

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
    void materialCrudShouldWork() throws Exception {
        createMaterial("""
            {
              "materialCode": "MAT-901",
              "materialName": "測試原料",
              "baseUnit": "g",
              "active": true,
              "description": "create"
            }
            """);

        Long materialId = materialRepository.findByMaterialCode("MAT-901").orElseThrow().getId();

        mockMvc.perform(get("/api/materials/{id}", materialId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.materialCode").value("MAT-901"))
            .andExpect(jsonPath("$.materialName").value("測試原料"))
            .andExpect(jsonPath("$.baseUnit").value("g"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.description").value("create"));

        mockMvc.perform(put("/api/materials/{id}", materialId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "materialCode": "MAT-901A",
                      "materialName": "測試原料-更新",
                      "baseUnit": "kg",
                      "active": false,
                      "description": "updated"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.materialCode").value("MAT-901A"))
            .andExpect(jsonPath("$.materialName").value("測試原料-更新"))
            .andExpect(jsonPath("$.baseUnit").value("kg"))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.description").value("updated"));

        mockMvc.perform(delete("/api/materials/{id}", materialId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/materials/{id}", materialId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.materialCode").value("MAT-901A"));
    }

    @Test
    void materialListShouldContainCreatedMaterial() throws Exception {
        materialRepository.save(new Material("MAT-902", "列表原料", "kg"));

        mockMvc.perform(get("/api/materials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.materialCode=='MAT-902')]").exists());
    }

    private void createMaterial(String body) throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists());
    }
}
