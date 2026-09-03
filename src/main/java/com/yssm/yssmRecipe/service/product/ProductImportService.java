package com.yssm.yssmRecipe.service.product;

import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.domain.recipe.ProductPackaging;
import com.yssm.yssmRecipe.domain.recipe.ProductRecipeMapping;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.dto.product.ProductImportResponse;
import com.yssm.yssmRecipe.repository.ProductPackagingRepository;
import com.yssm.yssmRecipe.repository.ProductRepository;
import com.yssm.yssmRecipe.repository.ProductRecipeMappingRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
public class ProductImportService {

    private final ProductRepository productRepository;
    private final ProductPackagingRepository productPackagingRepository;
    private final ProductRecipeMappingRepository productRecipeMappingRepository;
    private final RecipeRepository recipeRepository;

    @Transactional
    public ProductImportResponse importFromExcel(MultipartFile file) {
        List<ImportRow> rows = readRows(file);
        Set<String> seenCodes = new HashSet<>();
        int createdCount = 0;
        int updatedCount = 0;

        for (ImportRow row : rows) {
            String productCode = normalizeRequired(row.productCode(), row.lineNumber(), "商品代號");
            if (!seenCodes.add(productCode)) {
                throw new IllegalArgumentException("檔案內重複的商品代號: " + row.productCode());
            }

            String productName = normalizeRequired(row.productName(), row.lineNumber(), "商品名稱");
            Integer safetyStock = parseInteger(normalizeRequired(row.safetyStock(), row.lineNumber(), "安全庫存"), row.lineNumber(), "安全庫存");
            Integer maxStock = parseInteger(normalizeRequired(row.maxStock(), row.lineNumber(), "最大庫存"), row.lineNumber(), "最大庫存");
            String erpUnit = normalizeRequired(row.erpUnit(), row.lineNumber(), "ERP 單位");
            boolean active = row.active() == null || row.active();
            String packagingErpUnit = normalizeRequired(row.packagingErpUnit(), row.lineNumber(), "包裝 ERP 單位");
            BigDecimal gramWeightPerErpUnit = parseDecimal(normalizeRequired(row.gramWeightPerErpUnit(), row.lineNumber(), "1 ERP 單位 = 幾克"), row.lineNumber(), "1 ERP 單位 = 幾克");
            String packagingDescription = normalizeOptional(row.packagingDescription());
            String recipeCode = normalizeOptional(row.recipeCode());

            Product product = productRepository.findByProductCode(productCode)
                .orElseGet(Product::new);
            boolean existed = product.getId() != null;
            product.setProductCode(productCode);
            product.setProductName(productName);
            product.setSafetyStock(safetyStock);
            product.setMaxStock(maxStock);
            product.setErpUnit(erpUnit);
            product.setActive(active);

            Product saved = productRepository.save(product);
            replacePackaging(saved, packagingErpUnit, gramWeightPerErpUnit, packagingDescription);
            replaceRecipeMapping(saved, recipeCode, row.lineNumber());

            if (existed) {
                updatedCount++;
            } else {
                createdCount++;
            }
        }

        return new ProductImportResponse(safeFileName(file), rows.size(), createdCount, updatedCount, Instant.now());
    }

    private void replacePackaging(Product product, String erpUnit, BigDecimal gramWeightPerErpUnit, String description) {
        productPackagingRepository.findFirstByProductIdAndActiveTrueOrderByIdDesc(product.getId()).ifPresent(existing -> {
            existing.setActive(false);
            productPackagingRepository.save(existing);
        });
        ProductPackaging packaging = new ProductPackaging(product, erpUnit, gramWeightPerErpUnit, description);
        packaging.setActive(true);
        productPackagingRepository.save(packaging);
    }

    private void replaceRecipeMapping(Product product, String recipeCode, int lineNumber) {
        productRecipeMappingRepository.findByProductIdOrderByDisplayOrderAscIdAsc(product.getId()).forEach(existing -> {
            existing.setActive(false);
            existing.setPrimaryMapping(false);
            productRecipeMappingRepository.save(existing);
        });
        if (recipeCode == null || recipeCode.isBlank()) {
            return;
        }
        Recipe recipe = recipeRepository.findByRecipeCode(recipeCode)
            .orElseThrow(() -> new IllegalArgumentException("第 " + lineNumber + " 列找不到配方代號: " + recipeCode));
        productRecipeMappingRepository.save(new ProductRecipeMapping(product, recipe, true, true, 1));
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

                String productCode = formatter.formatCellValue(row.getCell(indexes.productCodeIndex())).trim();
                String productName = formatter.formatCellValue(row.getCell(indexes.productNameIndex())).trim();
                String safetyStock = formatter.formatCellValue(row.getCell(indexes.safetyStockIndex())).trim();
                String maxStock = formatter.formatCellValue(row.getCell(indexes.maxStockIndex())).trim();
                String erpUnit = formatter.formatCellValue(row.getCell(indexes.erpUnitIndex())).trim();
                String active = indexes.activeIndex() == null ? "" : formatter.formatCellValue(row.getCell(indexes.activeIndex())).trim();
                String recipeCode = indexes.recipeCodeIndex() == null ? "" : formatter.formatCellValue(row.getCell(indexes.recipeCodeIndex())).trim();
                String packagingErpUnit = formatter.formatCellValue(row.getCell(indexes.packagingErpUnitIndex())).trim();
                String gramWeightPerErpUnit = formatter.formatCellValue(row.getCell(indexes.gramWeightPerErpUnitIndex())).trim();
                String packagingDescription = indexes.packagingDescriptionIndex() == null ? "" : formatter.formatCellValue(row.getCell(indexes.packagingDescriptionIndex())).trim();

                if (productCode.isEmpty() && productName.isEmpty() && safetyStock.isEmpty() && maxStock.isEmpty() && erpUnit.isEmpty() && active.isEmpty() && recipeCode.isEmpty() && packagingErpUnit.isEmpty() && gramWeightPerErpUnit.isEmpty() && packagingDescription.isEmpty()) {
                    continue;
                }

                rows.add(new ImportRow(productCode, productName, safetyStock, maxStock, erpUnit, parseBoolean(active, rowIndex + 1), recipeCode, packagingErpUnit, gramWeightPerErpUnit, packagingDescription, rowIndex + 1));
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
        int productCodeIndex = findRequiredColumnIndex(headers, "productCode", "product_code", "商品代號", "商品編號", "代號");
        int productNameIndex = findRequiredColumnIndex(headers, "productName", "product_name", "商品名稱", "名稱");
        int safetyStockIndex = findRequiredColumnIndex(headers, "safetyStock", "safety_stock", "安全庫存", "安全存量");
        int maxStockIndex = findRequiredColumnIndex(headers, "maxStock", "max_stock", "最大庫存");
        int erpUnitIndex = findRequiredColumnIndex(headers, "erpUnit", "erp_unit", "ERP單位", "erp單位", "單位");
        Integer activeIndex = findOptionalColumnIndex(headers, "active", "啟用", "狀態");
        Integer recipeCodeIndex = findOptionalColumnIndex(headers, "recipeCode", "recipe_code", "配方代號", "對應配方");
        int packagingErpUnitIndex = findRequiredColumnIndex(headers, "packagingErpUnit", "packaging_erp_unit", "包裝ERP單位", "包裝ERP 單位");
        int gramWeightPerErpUnitIndex = findRequiredColumnIndex(headers, "gramWeightPerErpUnit", "gram_weight_per_erp_unit", "1ERP單位=幾克", "1 ERP 單位 = 幾克", "換算重量");
        Integer packagingDescriptionIndex = findOptionalColumnIndex(headers, "packagingDescription", "packaging_description", "包裝說明", "說明", "備註");
        return new ColumnIndexes(productCodeIndex, productNameIndex, safetyStockIndex, maxStockIndex, erpUnitIndex, activeIndex, recipeCodeIndex, packagingErpUnitIndex, gramWeightPerErpUnitIndex, packagingDescriptionIndex);
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

    private Integer parseInteger(String rawValue, int lineNumber, String fieldName) {
        try {
            return Integer.valueOf(rawValue.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列 " + fieldName + " 必須是整數");
        }
    }

    private BigDecimal parseDecimal(String rawValue, int lineNumber, String fieldName) {
        try {
            return new BigDecimal(rawValue.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列 " + fieldName + " 必須是數字");
        }
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

    private record ImportRow(
        String productCode,
        String productName,
        String safetyStock,
        String maxStock,
        String erpUnit,
        Boolean active,
        String recipeCode,
        String packagingErpUnit,
        String gramWeightPerErpUnit,
        String packagingDescription,
        int lineNumber
    ) {
    }

    private record ColumnIndexes(
        int productCodeIndex,
        int productNameIndex,
        int safetyStockIndex,
        int maxStockIndex,
        int erpUnitIndex,
        Integer activeIndex,
        Integer recipeCodeIndex,
        int packagingErpUnitIndex,
        int gramWeightPerErpUnitIndex,
        Integer packagingDescriptionIndex
    ) {
    }
}
