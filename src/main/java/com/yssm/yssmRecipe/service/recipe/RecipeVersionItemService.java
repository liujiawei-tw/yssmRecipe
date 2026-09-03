package com.yssm.yssmRecipe.service.recipe;

import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemDetailResponse;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemImportResult;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemUpsertRequest;
import com.yssm.yssmRecipe.exception.NotFoundException;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionItemRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class RecipeVersionItemService {

    private final RecipeVersionItemRepository recipeVersionItemRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final MaterialRepository materialRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionItemSearchService recipeVersionItemSearchService;
    private final RecipeVersionService recipeVersionService;

    @Transactional(readOnly = true)
    public List<RecipeVersionItemDetailResponse> list(
        Long recipeId,
        Long recipeVersionId,
        Long materialId,
        String search,
        String recipeSearch,
        String versionSearch,
        String materialSearch
    ) {
        return recipeVersionItemSearchService.search(recipeId, recipeVersionId, materialId, search, recipeSearch, versionSearch, materialSearch);
    }

    @Transactional(readOnly = true)
    public RecipeVersionItemDetailResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public RecipeVersionItemDetailResponse create(RecipeVersionItemUpsertRequest request) {
        RecipeVersion version = findVersion(request.recipeVersionId());
        Material material = findMaterial(request.materialId());
        ensureUniqueWithinVersion(version.getId(), null, material.getId(), request.displayOrder());

        RecipeVersionItem item = new RecipeVersionItem(version, material, request.ratio(), request.displayOrder());
        return toResponse(recipeVersionItemRepository.save(item));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public RecipeVersionItemDetailResponse update(Long id, RecipeVersionItemUpsertRequest request, boolean force) {
        RecipeVersionItem item = find(id);
        RecipeVersion sourceVersion = item.getRecipeVersion();
        RecipeVersion targetVersion = findVersion(request.recipeVersionId());
        Material material = findMaterial(request.materialId());

        if (!sourceVersion.getId().equals(targetVersion.getId()) && recipeVersionItemRepository.countByRecipeVersionId(sourceVersion.getId()) <= 1) {
            throw new IllegalArgumentException("配方版本至少需要一筆原料");
        }

        if (force) {
            normalizeDisplayOrder(targetVersion.getId(), item.getId(), request.displayOrder());
        } else {
            ensureUniqueWithinVersion(targetVersion.getId(), item.getId(), material.getId(), request.displayOrder());
        }
        ensureUniqueMaterialWithinVersion(targetVersion.getId(), item.getId(), material.getId());

        item.setRecipeVersion(targetVersion);
        item.setMaterial(material);
        item.setRatio(request.ratio());
        item.setDisplayOrder(request.displayOrder());
        return toResponse(recipeVersionItemRepository.save(item));
    }

    public RecipeVersionItemDetailResponse update(Long id, RecipeVersionItemUpsertRequest request) {
        return update(id, request, false);
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public void delete(Long id) {
        RecipeVersionItem item = find(id);
        recipeVersionItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(
        Long recipeId,
        Long recipeVersionId,
        Long materialId,
        String search,
        String recipeSearch,
        String versionSearch,
        String materialSearch
    ) {
        List<RecipeVersionItemDetailResponse> rows = list(recipeId, recipeVersionId, materialId, search, recipeSearch, versionSearch, materialSearch);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("配方比例明細");
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {
                "ID",
                "配方代碼",
                "配方名稱",
                "版本日期",
                "版本狀態",
                "基準重量(g)",
                "原料代碼",
                "原料名稱",
                "比例",
                "排序",
                "建立時間"
            };

            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (RecipeVersionItemDetailResponse row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                writeCell(excelRow, 0, row.id());
                writeCell(excelRow, 1, row.recipeCode());
                writeCell(excelRow, 2, row.recipeName());
                writeCell(excelRow, 3, row.versionDate() == null ? null : row.versionDate().toString());
                writeCell(excelRow, 4, row.versionStatus());
                writeCell(excelRow, 5, row.baseWeightG());
                writeCell(excelRow, 6, row.materialCode());
                writeCell(excelRow, 7, row.materialName());
                writeCell(excelRow, 8, row.ratio());
                writeCell(excelRow, 9, row.displayOrder());
                writeCell(excelRow, 10, row.createdAt() == null ? null : row.createdAt().toString());
            }

            for (int index = 0; index < headers.length; index++) {
                sheet.autoSizeColumn(index);
            }

            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=recipe-version-items.xlsx")
                .body(bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("匯出 Excel 失敗: " + ex.getMessage(), ex);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public RecipeVersionItemImportResult importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("請先選擇 Excel 檔案");
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = readHeaderIndexes(sheet.getRow(0));
            List<ImportRow> rows = readImportRows(sheet, headers);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("Excel 內沒有可匯入的資料");
            }

            Map<String, List<ImportRow>> groupedRows = rows.stream()
                .collect(Collectors.groupingBy(ImportRow::versionKey, LinkedHashMap::new, Collectors.toList()));

            int importedVersionCount = 0;
            List<RecipeVersionItem> itemsToSave = new ArrayList<>();
            for (List<ImportRow> groupRows : groupedRows.values()) {
                ImportRow firstRow = groupRows.get(0);
                RecipeVersion version = findOrCreateVersion(firstRow);

                List<RecipeVersionItem> existingItems = recipeVersionItemRepository.findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(version.getId());
                Map<Long, RecipeVersionItem> itemsByMaterialId = new LinkedHashMap<>();
                for (RecipeVersionItem existingItem : existingItems) {
                    itemsByMaterialId.put(existingItem.getMaterial().getId(), existingItem);
                }

                for (int index = 0; index < groupRows.size(); index++) {
                    ImportRow row = groupRows.get(index);
                    Material material = findMaterialByCode(row.materialCode());
                    Integer displayOrder = index + 1;
                    RecipeVersionItem existingItem = itemsByMaterialId.get(material.getId());
                    if (existingItem != null) {
                        existingItem.setRatio(row.ratio());
                        existingItem.setDisplayOrder(displayOrder);
                        itemsToSave.add(existingItem);
                        continue;
                    }

                    RecipeVersionItem newItem = new RecipeVersionItem(version, material, row.ratio(), displayOrder);
                    itemsToSave.add(newItem);
                }

                importedVersionCount++;
            }

            recipeVersionItemRepository.saveAll(itemsToSave);
            return new RecipeVersionItemImportResult(importedVersionCount, rows.size());
        } catch (IOException ex) {
            throw new IllegalArgumentException("匯入 Excel 失敗: " + ex.getMessage(), ex);
        }
    }

    private RecipeVersionItem find(Long id) {
        return recipeVersionItemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到配方比例明細: " + id));
    }

    private RecipeVersion findVersion(Long id) {
        return recipeVersionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到配方版本: " + id));
    }

    private RecipeVersion findOrCreateVersion(ImportRow row) {
        Recipe recipe = findRecipeByCode(row.recipeCode());
        RecipeVersion version = recipeVersionRepository.findByRecipeIdAndVersionDate(recipe.getId(), row.versionDate())
            .orElseGet(() -> new RecipeVersion(recipe, row.versionDate(), row.baseWeightG(), row.status()));
        version.setBaseWeightG(row.baseWeightG());
        version.setStatus(row.status());
        version.setCreatedBy(StringUtils.hasText(row.createdBy()) ? row.createdBy().trim() : null);
        RecipeVersion saved = recipeVersionRepository.save(version);
        recipeVersionService.ensureSingleActiveVersion(saved.getRecipe().getId(), saved.getId(), saved.getStatus());
        return saved;
    }

    private Recipe findRecipeByCode(String recipeCode) {
        return recipeRepository.findByRecipeCode(recipeCode)
            .orElseThrow(() -> new NotFoundException("找不到配方: " + recipeCode));
    }

    private Material findMaterialByCode(String materialCode) {
        return materialRepository.findByMaterialCode(materialCode)
            .orElseThrow(() -> new NotFoundException("找不到原料: " + materialCode));
    }

    private Material findMaterial(Long id) {
        return materialRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到原料: " + id));
    }

    private void ensureUniqueWithinVersion(Long recipeVersionId, Long currentItemId, Long materialId, Integer displayOrder) {
        List<RecipeVersionItem> items = recipeVersionItemRepository.findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(recipeVersionId);
        boolean duplicateMaterial = items.stream()
            .anyMatch(item -> !item.getId().equals(currentItemId) && item.getMaterial().getId().equals(materialId));
        if (duplicateMaterial) {
            throw new IllegalArgumentException("同一配方版本內不能重複使用相同原料");
        }

        boolean duplicateDisplayOrder = items.stream()
            .anyMatch(item -> !item.getId().equals(currentItemId) && item.getDisplayOrder().equals(displayOrder));
        if (duplicateDisplayOrder) {
            throw new IllegalArgumentException("同一配方版本內的排序不能重複");
        }
    }

    private void ensureUniqueMaterialWithinVersion(Long recipeVersionId, Long currentItemId, Long materialId) {
        boolean duplicateMaterial = recipeVersionItemRepository.findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(recipeVersionId).stream()
            .anyMatch(item -> !item.getId().equals(currentItemId) && item.getMaterial().getId().equals(materialId));
        if (duplicateMaterial) {
            throw new IllegalArgumentException("同一配方版本內不能重複使用相同原料");
        }
    }

    private void normalizeDisplayOrder(Long recipeVersionId, Long currentItemId, Integer requestedDisplayOrder) {
        List<RecipeVersionItem> siblings = recipeVersionItemRepository.findByRecipeVersionIdOrderByDisplayOrderAscIdAsc(recipeVersionId).stream()
            .filter(item -> !item.getId().equals(currentItemId))
            .toList();
        int nextOrder = requestedDisplayOrder == null ? 1 : requestedDisplayOrder + 1;
        List<RecipeVersionItem> changedItems = new ArrayList<>();
        for (RecipeVersionItem sibling : siblings) {
            if (sibling.getDisplayOrder() == null || sibling.getDisplayOrder() < nextOrder) {
                continue;
            }
            sibling.setDisplayOrder(nextOrder++);
            changedItems.add(sibling);
        }
        if (!changedItems.isEmpty()) {
            recipeVersionItemRepository.saveAll(changedItems);
        }
    }

    private RecipeVersionItemDetailResponse toResponse(RecipeVersionItem item) {
        RecipeVersion version = item.getRecipeVersion();
        return new RecipeVersionItemDetailResponse(
            item.getId(),
            version.getId(),
            version.getRecipe().getId(),
            version.getRecipe().getRecipeCode(),
            version.getRecipe().getRecipeName(),
            version.getVersionDate(),
            version.getStatus().name(),
            version.getBaseWeightG(),
            item.getMaterial().getId(),
            item.getMaterial().getMaterialCode(),
            item.getMaterial().getMaterialName(),
            item.getRatio(),
            item.getDisplayOrder(),
            item.getCreatedAt()
        );
    }

    private Map<String, Integer> readHeaderIndexes(Row headerRow) {
        if (headerRow == null) {
            throw new IllegalArgumentException("Excel 第一列必須是欄位名稱");
        }
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> headers = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = normalizeHeader(formatter.formatCellValue(cell));
            if (StringUtils.hasText(header)) {
                headers.put(header, cell.getColumnIndex());
            }
        }
        return headers;
    }

    private List<ImportRow> readImportRows(Sheet sheet, Map<String, Integer> headers) {
        List<ImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row, formatter)) {
                continue;
            }

            String recipeCode = readRequiredText(row, headers, formatter, "recipe_code", "配方代號");
            LocalDate versionDate = readRequiredDate(row, headers, formatter, "version_date", "版本日期");
            RecipeVersionStatus status = readStatus(row, headers, formatter);
            BigDecimal baseWeightG = readRequiredDecimal(row, headers, formatter, "base_weight_g", "基準重量(g)");
            String materialCode = readRequiredText(row, headers, formatter, "material_code", "原料代號");
            BigDecimal ratio = readRequiredDecimal(row, headers, formatter, "ratio", "比例");
            String createdBy = readOptionalText(row, headers, formatter, "created_by", "建立者");

            Recipe recipe = findRecipeByCode(recipeCode);
            Material material = findMaterialByCode(materialCode);

            rows.add(new ImportRow(
                recipe.getId(),
                recipeCode,
                versionDate,
                status,
                baseWeightG,
                material.getId(),
                materialCode,
                ratio,
                null,
                createdBy
            ));
        }
        return rows;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (StringUtils.hasText(formatter.formatCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    private String readRequiredText(Row row, Map<String, Integer> headers, DataFormatter formatter, String... candidates) {
        String value = readOptionalText(row, headers, formatter, candidates);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Excel 欄位不可空白: " + candidates[0]);
        }
        return value.trim();
    }

    private String readOptionalText(Row row, Map<String, Integer> headers, DataFormatter formatter, String... candidates) {
        Cell cell = findCell(row, headers, candidates);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDate readRequiredDate(Row row, Map<String, Integer> headers, DataFormatter formatter, String... candidates) {
        Cell cell = findCell(row, headers, candidates);
        if (cell == null) {
            throw new IllegalArgumentException("Excel 欄位不可空白: " + candidates[0]);
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = formatter.formatCellValue(cell).trim();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Excel 欄位不可空白: " + candidates[0]);
        }
        return LocalDate.parse(value.replace('/', '-'));
    }

    private BigDecimal readRequiredDecimal(Row row, Map<String, Integer> headers, DataFormatter formatter, String... candidates) {
        return new BigDecimal(readRequiredText(row, headers, formatter, candidates));
    }

    private Integer readRequiredInteger(Row row, Map<String, Integer> headers, DataFormatter formatter, String... candidates) {
        return Integer.valueOf(readRequiredText(row, headers, formatter, candidates));
    }

    private RecipeVersionStatus readStatus(Row row, Map<String, Integer> headers, DataFormatter formatter) {
        String value = readOptionalText(row, headers, formatter, "version_status", "版本狀態");
        if (!StringUtils.hasText(value)) {
            return RecipeVersionStatus.ACTIVE;
        }
        return RecipeVersionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private Cell findCell(Row row, Map<String, Integer> headers, String... candidates) {
        for (String candidate : candidates) {
            Integer columnIndex = headers.get(normalizeHeader(candidate));
            if (columnIndex != null) {
                return row.getCell(columnIndex);
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replace("（", "(")
            .replace("）", ")")
            .replace(" ", "")
            .replace("-", "_")
            .replace("/", "_");
    }

    private record ImportRow(
        Long recipeId,
        String recipeCode,
        LocalDate versionDate,
        RecipeVersionStatus status,
        BigDecimal baseWeightG,
        Long materialId,
        String materialCode,
        BigDecimal ratio,
        Integer displayOrder,
        String createdBy
    ) {
        private String versionKey() {
            return recipeCode + "|" + versionDate;
        }
    }

    private void writeCell(Row row, int columnIndex, Object value) {
        Cell cell = row.createCell(columnIndex);
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        cell.setCellValue(value.toString());
    }
}
