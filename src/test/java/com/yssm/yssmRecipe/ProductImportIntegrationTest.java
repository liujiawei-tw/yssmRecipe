package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.repository.ProductRepository;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductImportIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    ProductRepository productRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void shouldImportAndUpdateProductsFromExcel() throws Exception {
        MockMultipartFile firstFile = excelFile(
            "products-import.xlsx",
            new String[][] {
                {"product_code", "product_name", "safety_stock", "max_stock", "erp_unit", "active", "recipe_code", "packaging_erp_unit", "gram_weight_per_erp_unit", "packaging_description"},
                {"P-XLS-001", "匯入商品一", "10", "20", "箱", "1", "", "箱", "1200", "first"},
                {"P-XLS-002", "匯入商品二", "5", "15", "袋", "0", "", "袋", "500", "second"}
            }
        );

        mockMvc.perform(multipart("/api/products/import").file(firstFile))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(2))
            .andExpect(jsonPath("$.createdCount").value(2))
            .andExpect(jsonPath("$.updatedCount").value(0));

        Product created = productRepository.findByProductCode("P-XLS-001").orElseThrow();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getProductName()).isEqualTo("匯入商品一");
        assertThat(created.getSafetyStock()).isEqualTo(10);

        MockMultipartFile secondFile = excelFile(
            "products-import-update.xlsx",
            new String[][] {
                {"product_code", "product_name", "safety_stock", "max_stock", "erp_unit", "active", "recipe_code", "packaging_erp_unit", "gram_weight_per_erp_unit", "packaging_description"},
                {"P-XLS-001", "匯入商品一-更新", "12", "24", "盒", "false", "", "盒", "1500", "updated"}
            }
        );

        mockMvc.perform(multipart("/api/products/import").file(secondFile))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(1))
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.updatedCount").value(1));

        Product updated = productRepository.findByProductCode("P-XLS-001").orElseThrow();
        assertThat(updated.getProductName()).isEqualTo("匯入商品一-更新");
        assertThat(updated.getSafetyStock()).isEqualTo(12);
        assertThat(updated.getMaxStock()).isEqualTo(24);
        assertThat(updated.getErpUnit()).isEqualTo("盒");
        assertThat(updated.isActive()).isFalse();
    }

    private MockMultipartFile excelFile(String fileName, String[][] rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("products");
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                for (int cellIndex = 0; cellIndex < rows[rowIndex].length; cellIndex++) {
                    row.createCell(cellIndex).setCellValue(rows[rowIndex][cellIndex]);
                }
            }
            workbook.write(outputStream);
            return new MockMultipartFile(
                "file",
                fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
            );
        }
    }
}
