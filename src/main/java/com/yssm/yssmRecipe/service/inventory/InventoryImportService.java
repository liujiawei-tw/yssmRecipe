package com.yssm.yssmRecipe.service.inventory;

import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.MaterialInventorySnapshot;
import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.domain.recipe.ProductStockSnapshot;
import com.yssm.yssmRecipe.dto.inventory.InventoryImportResponse;
import com.yssm.yssmRecipe.repository.MaterialInventorySnapshotRepository;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.ProductRepository;
import com.yssm.yssmRecipe.repository.ProductStockSnapshotRepository;
import com.yssm.yssmRecipe.service.procurement.PurchaseSuggestionService;
import com.yssm.yssmRecipe.service.production.ProductionPlanService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class InventoryImportService {

    private final ProductRepository productRepository;
    private final MaterialRepository materialRepository;
    private final ProductStockSnapshotRepository productStockSnapshotRepository;
    private final MaterialInventorySnapshotRepository materialInventorySnapshotRepository;
    private final ProductionPlanService productionPlanService;
    private final PurchaseSuggestionService purchaseSuggestionService;

    @Transactional
    public InventoryImportResponse importProductStock(MultipartFile file) {
        List<ImportRow> rows = readRows(file, InventoryType.PRODUCT);
        Set<String> seenCodes = new HashSet<>();
        List<ProductStockSnapshot> snapshots = new ArrayList<>();
        for (ImportRow row : rows) {
            String code = normalizeCode(row.code());
            if (!seenCodes.add(code)) {
                throw new IllegalArgumentException("檔案內重複的商品代號: " + row.code());
            }
            int stockQuantity = parseInteger(row.quantity(), row.lineNumber(), "商品庫存數量");
            if (stockQuantity < 0) {
                throw new IllegalArgumentException("第 " + row.lineNumber() + " 列商品庫存不可小於 0");
            }
            Product product = productRepository.findByProductCode(code)
                .orElseThrow(() -> new IllegalArgumentException("找不到商品代號: " + row.code()));
            snapshots.add(new ProductStockSnapshot(product, stockQuantity, safeFileName(file)));
        }
        List<ProductStockSnapshot> savedSnapshots = productStockSnapshotRepository.saveAll(snapshots);
        refreshPlanningData(savedSnapshots);
        return new InventoryImportResponse(safeFileName(file), "PRODUCT", snapshots.size(), Instant.now());
    }

    @Transactional
    public InventoryImportResponse importMaterialStock(MultipartFile file) {
        List<ImportRow> rows = readRows(file, InventoryType.MATERIAL);
        Set<String> seenCodes = new HashSet<>();
        List<MaterialInventorySnapshot> snapshots = new ArrayList<>();
        for (ImportRow row : rows) {
            String code = normalizeCode(row.code());
            if (!seenCodes.add(code)) {
                throw new IllegalArgumentException("檔案內重複的原料代號: " + row.code());
            }
            BigDecimal stockQuantity = parseDecimal(row.quantity(), row.lineNumber(), "原料盤點數量");
            if (stockQuantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("第 " + row.lineNumber() + " 列原料盤點不可小於 0");
            }
            Material material = materialRepository.findByMaterialCode(code)
                .orElseThrow(() -> new IllegalArgumentException("找不到原料代號: " + row.code()));
            snapshots.add(new MaterialInventorySnapshot(material, stockQuantity, safeFileName(file)));
        }
        materialInventorySnapshotRepository.saveAll(snapshots);
        refreshPurchaseSuggestion();
        return new InventoryImportResponse(safeFileName(file), "MATERIAL", snapshots.size(), Instant.now());
    }

    private void refreshPlanningData(List<ProductStockSnapshot> productStockSnapshots) {
        try {
            productionPlanService.refreshFromProductStockSnapshots(productStockSnapshots);
            purchaseSuggestionService.generate();
        } catch (RuntimeException ex) {
            log.warn("庫存匯入完成，但更新生產計畫/請購分析失敗", ex);
        }
    }

    private void refreshPurchaseSuggestion() {
        try {
            purchaseSuggestionService.generate();
        } catch (RuntimeException ex) {
            log.warn("庫存匯入完成，但更新請購分析失敗", ex);
        }
    }

    @Transactional(readOnly = true)
    public Integer findLatestProductStockByProductId(Long productId) {
        return productStockSnapshotRepository.findTopByProductIdOrderByImportedAtDescIdDesc(productId)
            .map(ProductStockSnapshot::getStockQuantity)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public BigDecimal findLatestMaterialStockByMaterialId(Long materialId) {
        return materialInventorySnapshotRepository.findTopByMaterialIdOrderByImportedAtDescIdDesc(materialId)
            .map(MaterialInventorySnapshot::getStockQuantity)
            .orElse(null);
    }

    private List<ImportRow> readRows(MultipartFile file, InventoryType inventoryType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("請上傳匯入檔");
        }
        String filename = safeFileName(file);
        try {
            if (filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                return readCsvRows(file.getInputStream(), inventoryType);
            }
            return readExcelRows(file.getInputStream(), inventoryType);
        } catch (IOException ex) {
            throw new IllegalArgumentException("無法讀取匯入檔: " + ex.getMessage(), ex);
        }
    }

    private List<ImportRow> readCsvRows(InputStream inputStream, InventoryType inventoryType) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("匯入檔沒有內容");
            }
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = parseDelimitedLine(headerLine, delimiter);
            ColumnIndexes indexes = resolveColumns(headers, inventoryType);
            List<ImportRow> rows = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> cells = parseDelimitedLine(line, delimiter);
                rows.add(extractRow(cells, indexes, lineNumber));
            }
            validateRows(rows);
            return rows;
        }
    }

    private List<ImportRow> readExcelRows(InputStream inputStream, InventoryType inventoryType) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("匯入檔沒有標題列");
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            headerRow.forEach(cell -> headers.add(formatter.formatCellValue(cell)));
            ColumnIndexes indexes = resolveColumns(headers, inventoryType);

            List<ImportRow> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String code = formatter.formatCellValue(row.getCell(indexes.codeIndex())).trim();
                String quantity = formatter.formatCellValue(row.getCell(indexes.quantityIndex())).trim();
                if (code.isEmpty() && quantity.isEmpty()) {
                    continue;
                }
                rows.add(new ImportRow(code, quantity, rowIndex + 1));
            }
            validateRows(rows);
            return rows;
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("無法解析 Excel 匯入檔: " + ex.getMessage(), ex);
        }
    }

    private ColumnIndexes resolveColumns(List<String> headers, InventoryType inventoryType) {
        int codeIndex;
        int quantityIndex;
        if (inventoryType == InventoryType.PRODUCT) {
            codeIndex = findColumnIndex(headers, "productCode", "product_code", "商品代號", "商品編號", "料號");
            quantityIndex = findColumnIndex(headers, "stockQuantity", "stock_quantity", "現有庫存", "庫存", "庫存數量", "數量");
        } else {
            codeIndex = findColumnIndex(headers, "materialCode", "material_code", "原料代號", "原料編號", "料號");
            quantityIndex = findColumnIndex(headers, "stockQuantity", "stock_quantity", "現有庫存", "盤點庫存", "庫存", "庫存數量", "數量");
        }
        return new ColumnIndexes(codeIndex, quantityIndex);
    }

    private int findColumnIndex(List<String> headers, String... aliases) {
        for (int index = 0; index < headers.size(); index++) {
            String normalizedHeader = normalizeHeader(headers.get(index));
            for (String alias : aliases) {
                if (normalizedHeader.equals(normalizeHeader(alias))) {
                    return index;
                }
            }
        }
        throw new IllegalArgumentException("匯入檔缺少必要欄位: " + String.join("/", aliases));
    }

    private void validateRows(List<ImportRow> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("匯入檔沒有可處理的資料列");
        }
    }

    private ImportRow extractRow(List<String> cells, ColumnIndexes indexes, int lineNumber) {
        String code = getCellValue(cells, indexes.codeIndex());
        String quantity = getCellValue(cells, indexes.quantityIndex());
        if (code.isBlank() && quantity.isBlank()) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列沒有資料");
        }
        return new ImportRow(code.trim(), quantity.trim(), lineNumber);
    }

    private String getCellValue(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : "";
    }

    private int parseInteger(String rawValue, int lineNumber, String fieldName) {
        try {
            BigDecimal value = new BigDecimal(rawValue.trim());
            if (value.scale() > 0 && value.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException();
            }
            return value.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (Exception ex) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列 " + fieldName + " 必須是整數");
        }
    }

    private BigDecimal parseDecimal(String rawValue, int lineNumber, String fieldName) {
        try {
            return new BigDecimal(rawValue.trim()).setScale(6, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            throw new IllegalArgumentException("第 " + lineNumber + " 列 " + fieldName + " 必須是數字");
        }
    }

    private char detectDelimiter(String line) {
        int comma = count(line, ',');
        int tab = count(line, '\t');
        int semicolon = count(line, ';');
        if (tab >= comma && tab >= semicolon) {
            return '\t';
        }
        if (semicolon >= comma) {
            return ';';
        }
        return ',';
    }

    private int count(String line, char target) {
        int count = 0;
        for (int index = 0; index < line.length(); index++) {
            if (line.charAt(index) == target) {
                count++;
            }
        }
        return count;
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (inQuotes) {
                if (currentChar == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        current.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(currentChar);
                }
            } else if (currentChar == '"') {
                inQuotes = true;
            } else if (currentChar == delimiter) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String normalizeHeader(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("[\\s_\\-\\/]+", "");
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename == null || originalFilename.isBlank() ? "upload" : originalFilename;
    }

    private record ImportRow(String code, String quantity, int lineNumber) {
    }

    private record ColumnIndexes(int codeIndex, int quantityIndex) {
    }

    private enum InventoryType {
        PRODUCT,
        MATERIAL
    }
}
