package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.domain.recipe.ProductPackaging;
import com.yssm.yssmRecipe.domain.recipe.ProductRecipeMapping;
import com.yssm.yssmRecipe.domain.recipe.ProductStockSnapshot;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import com.yssm.yssmRecipe.repository.MaterialInventorySnapshotRepository;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.ProductPackagingRepository;
import com.yssm.yssmRecipe.repository.ProductRepository;
import com.yssm.yssmRecipe.repository.ProductRecipeMappingRepository;
import com.yssm.yssmRecipe.repository.ProductStockSnapshotRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryImportIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductStockSnapshotRepository productStockSnapshotRepository;

    @Autowired
    MaterialRepository materialRepository;

    @Autowired
    MaterialInventorySnapshotRepository materialInventorySnapshotRepository;

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    RecipeVersionRepository recipeVersionRepository;

    @Autowired
    ProductPackagingRepository productPackagingRepository;

    @Autowired
    ProductRecipeMappingRepository productRecipeMappingRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void shouldImportProductStockFromCsvAndUseLatestStockForProductionPlan() throws Exception {
        Product product = productRepository.save(new Product("PRD-IMP-001", "匯入商品", 20, 100, "PCS"));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "product-stock.csv",
            "text/csv",
            """
            product_code,stock_quantity
            PRD-IMP-001,42
            """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/inventory-imports/product-stock").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inventoryType").value("PRODUCT"))
            .andExpect(jsonPath("$.importedCount").value(1))
            .andExpect(jsonPath("$.sourceFileName").value("product-stock.csv"));

        ProductStockSnapshot snapshot = productStockSnapshotRepository
            .findTopByProductIdOrderByImportedAtDescIdDesc(product.getId())
            .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(snapshot.getStockQuantity()).isEqualTo(42);

        Recipe recipe = recipeRepository.save(new Recipe("REC-IMP-001", "匯入測試配方"));
        Material material = materialRepository.save(new Material("MAT-IMP-001", "匯入測試原料", "g"));
        RecipeVersion version = new RecipeVersion(recipe, LocalDate.of(2026, 8, 27), new BigDecimal("500"), RecipeVersionStatus.ACTIVE);
        version.getItems().add(new RecipeVersionItem(version, material, new BigDecimal("1"), 1));
        recipeVersionRepository.save(version);
        productPackagingRepository.save(new ProductPackaging(product, "PCS", new BigDecimal("10"), "10g/pcs"));
        productRecipeMappingRepository.save(new ProductRecipeMapping(product, recipe, true, true, 1));

        mockMvc.perform(post("/api/production-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "productId": %d
                    }
                    """.formatted(product.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(product.getId().intValue()))
            .andExpect(jsonPath("$.currentStock").value(42))
            .andExpect(jsonPath("$.suggestedQuantity").value(58));
    }

    @Test
    void shouldImportMaterialStockFromExcel() throws Exception {
        materialRepository.save(new Material("MAT-IMP-EXCEL", "Excel原料", "kg"));

        byte[] workbookBytes;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("material_code");
            header.createCell(1).setCellValue("stock_quantity");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("MAT-IMP-EXCEL");
            row.createCell(1).setCellValue(88.5);
            workbook.write(outputStream);
            workbookBytes = outputStream.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "material-stock.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookBytes
        );

        mockMvc.perform(multipart("/api/inventory-imports/material-stock").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inventoryType").value("MATERIAL"))
            .andExpect(jsonPath("$.importedCount").value(1))
            .andExpect(jsonPath("$.sourceFileName").value("material-stock.xlsx"));

        org.assertj.core.api.Assertions.assertThat(
            materialInventorySnapshotRepository.findTopByMaterialIdOrderByImportedAtDescIdDesc(
                materialRepository.findByMaterialCode("MAT-IMP-EXCEL").orElseThrow().getId()
            )
        ).isPresent();
    }

    @Test
    void shouldShowOnlyLatestImportedProductStockBatchInProductionPlans() throws Exception {
        Material material = materialRepository.save(new Material("MAT-BATCH-001", "批次原料", "g"));
        Recipe recipe = recipeRepository.save(new Recipe("REC-BATCH-001", "批次配方"));
        RecipeVersion version = new RecipeVersion(recipe, LocalDate.of(2026, 9, 4), new BigDecimal("100"), RecipeVersionStatus.ACTIVE);
        version.getItems().add(new RecipeVersionItem(version, material, new BigDecimal("1"), 1));
        recipeVersionRepository.save(version);

        Product productA = saveConfiguredProduct("PRD-BATCH-A", "批次商品A", recipe);
        saveConfiguredProduct("PRD-BATCH-B", "批次商品B", recipe);

        MockMultipartFile firstFile = new MockMultipartFile(
            "file",
            "product-stock-first.csv",
            "text/csv",
            """
            product_code,stock_quantity
            PRD-BATCH-A,10
            PRD-BATCH-B,20
            """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/inventory-imports/product-stock").file(firstFile))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(2));

        MockMultipartFile secondFile = new MockMultipartFile(
            "file",
            "product-stock-second.csv",
            "text/csv",
            """
            product_code,stock_quantity
            PRD-BATCH-A,30
            """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/inventory-imports/product-stock").file(secondFile))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(1));

        mockMvc.perform(get("/api/production-plans/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].productCode").value(productA.getProductCode()))
            .andExpect(jsonPath("$[0].currentStock").value(30));
    }

    @Test
    void shouldImportProductStockWhenSuggestedProductionQuantityIsZero() throws Exception {
        Material material = materialRepository.save(new Material("MAT-ZERO-001", "零生產原料", "g"));
        Recipe recipe = recipeRepository.save(new Recipe("REC-ZERO-001", "零生產配方"));
        RecipeVersion version = new RecipeVersion(recipe, LocalDate.of(2026, 9, 4), new BigDecimal("100"), RecipeVersionStatus.ACTIVE);
        version.getItems().add(new RecipeVersionItem(version, material, new BigDecimal("1"), 1));
        recipeVersionRepository.save(version);
        Product product = saveConfiguredProduct("PRD-ZERO-001", "零生產商品", recipe);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "product-stock-zero.csv",
            "text/csv",
            """
            product_code,stock_quantity
            PRD-ZERO-001,100
            """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/inventory-imports/product-stock").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(1));

        mockMvc.perform(get("/api/production-plans/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].productCode").value(product.getProductCode()))
            .andExpect(jsonPath("$[0].plannedQuantity").value(0))
            .andExpect(jsonPath("$[0].calculationWeightG").value(0))
            .andExpect(jsonPath("$[0].materialRequirements.length()").value(0));
    }

    private Product saveConfiguredProduct(String productCode, String productName, Recipe recipe) {
        Product product = productRepository.save(new Product(productCode, productName, 10, 100, "PCS"));
        productPackagingRepository.save(new ProductPackaging(product, "PCS", new BigDecimal("10"), "10g/pcs"));
        productRecipeMappingRepository.save(new ProductRecipeMapping(product, recipe, true, true, 1));
        return product;
    }
}
