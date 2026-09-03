package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.AbstractH2IntegrationTest;
import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.repository.MaterialRepository;
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

class MaterialImportIntegrationTest extends AbstractH2IntegrationTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    MaterialRepository materialRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void shouldImportAndUpdateMaterialsFromExcel() throws Exception {
        MockMultipartFile firstFile = excelFile(
            "materials-import.xlsx",
            new String[][] {
                {"material_code", "material_name", "base_unit", "active", "description"},
                {"MAT-XLS-001", "匯入原料一", "g", "1", "first"},
                {"MAT-XLS-002", "匯入原料二", "kg", "0", "second"}
            }
        );

        mockMvc.perform(multipart("/api/materials/import").file(firstFile))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(2))
            .andExpect(jsonPath("$.createdCount").value(2))
            .andExpect(jsonPath("$.updatedCount").value(0));

        Material created = materialRepository.findByMaterialCode("MAT-XLS-001").orElseThrow();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getMaterialName()).isEqualTo("匯入原料一");

        MockMultipartFile secondFile = excelFile(
            "materials-import-update.xlsx",
            new String[][] {
                {"material_code", "material_name", "base_unit", "active", "description"},
                {"MAT-XLS-001", "匯入原料一-更新", "kg", "false", "updated"}
            }
        );

        mockMvc.perform(multipart("/api/materials/import").file(secondFile))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(1))
            .andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.updatedCount").value(1));

        Material updated = materialRepository.findByMaterialCode("MAT-XLS-001").orElseThrow();
        assertThat(updated.getMaterialName()).isEqualTo("匯入原料一-更新");
        assertThat(updated.getBaseUnit()).isEqualTo("kg");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getDescription()).isEqualTo("updated");
    }

    private MockMultipartFile excelFile(String fileName, String[][] rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("materials");
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
