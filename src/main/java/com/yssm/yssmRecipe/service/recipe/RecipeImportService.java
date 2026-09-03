package com.yssm.yssmRecipe.service.recipe;

import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.dto.recipe.RecipeImportResponse;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class RecipeImportService {

    private final RecipeRepository recipeRepository;

    @Transactional
    public RecipeImportResponse importFromExcel(MultipartFile file) {
        List<ImportRow> rows = readRows(file);
        Set<String> seenCodes = new HashSet<>();
        List<Recipe> recipes = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;

        for (ImportRow row : rows) {
            String recipeCode = normalizeRequired(row.recipeCode(), row.lineNumber(), "配方代號");
            if (!seenCodes.add(recipeCode)) {
                throw new IllegalArgumentException("檔案內重複的配方代號: " + row.recipeCode());
            }

            String recipeName = normalizeRequired(row.recipeName(), row.lineNumber(), "配方名稱");
            boolean active = row.active() == null || row.active();
            String description = normalizeOptional(row.description());

            Recipe recipe = recipeRepository.findByRecipeCode(recipeCode)
                .orElseGet(Recipe::new);
            boolean existed = recipe.getId() != null;
            recipe.setRecipeCode(recipeCode);
            recipe.setRecipeName(recipeName);
            recipe.setActive(active);
            recipe.setDescription(description);
            recipes.add(recipe);

            if (existed) {
                updatedCount++;
            } else {
                createdCount++;
            }
        }

        recipeRepository.saveAll(recipes);
        return new RecipeImportResponse(safeFileName(file), rows.size(), createdCount, updatedCount, Instant.now());
    }

    private List<ImportRow> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("請上傳 Excel 檔");
        }
        String filename = safeFileName(file);
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new IllegalArgumentException("僅支援 Excel 檔案（.xlsx / .xls）");
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("匯入檔沒有工作表");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("匯入檔沒有標題列");
            }

            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            headerRow.forEach(cell -> headers.add(formatter.formatCellValue(cell)));
            ColumnIndexes indexes = resolveColumns(headers);

            List<ImportRow> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String recipeCode = formatter.formatCellValue(row.getCell(indexes.recipeCodeIndex())).trim();
                String recipeName = formatter.formatCellValue(row.getCell(indexes.recipeNameIndex())).trim();
                String active = indexes.activeIndex() == null ? "" : formatter.formatCellValue(row.getCell(indexes.activeIndex())).trim();
                String description = indexes.descriptionIndex() == null ? "" : formatter.formatCellValue(row.getCell(indexes.descriptionIndex())).trim();

                if (recipeCode.isEmpty() && recipeName.isEmpty() && active.isEmpty() && description.isEmpty()) {
                    continue;
                }

                rows.add(new ImportRow(recipeCode, recipeName, parseBoolean(active, rowIndex + 1), description, rowIndex + 1));
            }

            if (rows.isEmpty()) {
                throw new IllegalArgumentException("匯入檔沒有可處理的資料列");
            }

            return rows;
        } catch (IOException ex) {
            throw new IllegalArgumentException("無法讀取 Excel 匯入檔: " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("無法解析 Excel 匯入檔: " + ex.getMessage(), ex);
        }
    }

    private ColumnIndexes resolveColumns(List<String> headers) {
        int recipeCodeIndex = findRequiredColumnIndex(headers, "recipeCode", "recipe_code", "配方代號", "配方編號", "代號");
        int recipeNameIndex = findRequiredColumnIndex(headers, "recipeName", "recipe_name", "配方名稱", "名稱");
        Integer activeIndex = findOptionalColumnIndex(headers, "active", "啟用", "狀態");
        Integer descriptionIndex = findOptionalColumnIndex(headers, "description", "說明", "備註");
        return new ColumnIndexes(recipeCodeIndex, recipeNameIndex, activeIndex, descriptionIndex);
    }

    private int findRequiredColumnIndex(List<String> headers, String... aliases) {
        Integer index = findOptionalColumnIndex(headers, aliases);
        if (index == null) {
            throw new IllegalArgumentException("匯入檔缺少必要欄位: " + String.join("/", aliases));
        }
        return index;
    }

    private Integer findOptionalColumnIndex(List<String> headers, String... aliases) {
        for (int index = 0; index < headers.size(); index++) {
            String normalizedHeader = normalizeHeader(headers.get(index));
            for (String alias : aliases) {
                if (normalizedHeader.equals(normalizeHeader(alias))) {
                    return index;
                }
            }
        }
        return null;
    }

    private String normalizeRequired(String rawValue, int lineNumber, String fieldName) {
        String value = normalizeOptional(rawValue);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列 " + fieldName + " 不可空白");
        }
        return value;
    }

    private String normalizeOptional(String rawValue) {
        return rawValue == null ? null : rawValue.trim();
    }

    private Boolean parseBoolean(String rawValue, int lineNumber) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = normalizeHeader(rawValue);
        return switch (normalized) {
            case "1", "true", "yes", "y", "是", "啟用", "active" -> true;
            case "0", "false", "no", "n", "否", "停用", "inactive" -> false;
            default -> throw new IllegalArgumentException("第 " + lineNumber + " 列 啟用欄位必須是 1/0、true/false、是/否 或 啟用/停用");
        };
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-\\/]+", "");
    }

    private String safeFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename == null || originalFilename.isBlank() ? "upload" : originalFilename;
    }

    private record ImportRow(String recipeCode, String recipeName, Boolean active, String description, int lineNumber) {
    }

    private record ColumnIndexes(int recipeCodeIndex, int recipeNameIndex, Integer activeIndex, Integer descriptionIndex) {
    }
}
