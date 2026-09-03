package com.yssm.yssmRecipe.service.material;

import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.dto.material.MaterialImportResponse;
import com.yssm.yssmRecipe.dto.material.MaterialResponse;
import com.yssm.yssmRecipe.dto.material.MaterialUpsertRequest;
import com.yssm.yssmRecipe.exception.NotFoundException;
import com.yssm.yssmRecipe.repository.MaterialRepository;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;

    @Transactional(readOnly = true)
    public List<MaterialResponse> list() {
        return materialRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MaterialResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public MaterialResponse create(MaterialUpsertRequest request) {
        materialRepository.findByMaterialCode(request.materialCode()).ifPresent(existing -> {
            throw new IllegalArgumentException("materialCode 已存在");
        });
        Material material = new Material(request.materialCode(), request.materialName(), request.baseUnit());
        material.setActive(request.active() == null || request.active());
        material.setDescription(request.description());
        return toResponse(materialRepository.save(material));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public MaterialResponse update(Long id, MaterialUpsertRequest request) {
        Material material = find(id);
        if (!material.getMaterialCode().equals(request.materialCode())) {
            materialRepository.findByMaterialCode(request.materialCode()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("materialCode 已存在");
                }
            });
        }
        material.setMaterialCode(request.materialCode());
        material.setMaterialName(request.materialName());
        material.setBaseUnit(request.baseUnit());
        material.setActive(request.active() == null || request.active());
        material.setDescription(request.description());
        return toResponse(materialRepository.save(material));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public void delete(Long id) {
        Material material = find(id);
        try {
            materialRepository.delete(material);
            materialRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("原料仍被歷史資料或明細引用，無法刪除", ex);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public MaterialImportResponse importFromExcel(MultipartFile file) {
        List<ImportRow> rows = readRows(file);
        Set<String> seenCodes = new HashSet<>();
        List<Material> materials = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;

        for (ImportRow row : rows) {
            String materialCode = normalizeRequired(row.materialCode(), row.lineNumber(), "原料代號");
            if (!seenCodes.add(materialCode)) {
                throw new IllegalArgumentException("檔案內重複的原料代號: " + row.materialCode());
            }

            String materialName = normalizeRequired(row.materialName(), row.lineNumber(), "原料名稱");
            String baseUnit = normalizeRequired(row.baseUnit(), row.lineNumber(), "基準單位");
            boolean active = row.active() == null || row.active();
            String description = normalizeOptional(row.description());

            Material material = materialRepository.findByMaterialCode(materialCode)
                .orElseGet(Material::new);
            boolean existed = material.getId() != null;
            material.setMaterialCode(materialCode);
            material.setMaterialName(materialName);
            material.setBaseUnit(baseUnit);
            material.setActive(active);
            material.setDescription(description);
            materials.add(material);

            if (existed) {
                updatedCount++;
            } else {
                createdCount++;
            }
        }

        materialRepository.saveAll(materials);
        return new MaterialImportResponse(safeFileName(file), rows.size(), createdCount, updatedCount, Instant.now());
    }

    public Material find(Long id) {
        return materialRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到原料: " + id));
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

                String materialCode = formatter.formatCellValue(row.getCell(indexes.materialCodeIndex())).trim();
                String materialName = formatter.formatCellValue(row.getCell(indexes.materialNameIndex())).trim();
                String baseUnit = formatter.formatCellValue(row.getCell(indexes.baseUnitIndex())).trim();
                String active = indexes.activeIndex() == null ? "" : formatter.formatCellValue(row.getCell(indexes.activeIndex())).trim();
                String description = indexes.descriptionIndex() == null ? "" : formatter.formatCellValue(row.getCell(indexes.descriptionIndex())).trim();

                if (materialCode.isEmpty() && materialName.isEmpty() && baseUnit.isEmpty() && active.isEmpty() && description.isEmpty()) {
                    continue;
                }

                rows.add(new ImportRow(materialCode, materialName, baseUnit, parseBoolean(active, rowIndex + 1), description, rowIndex + 1));
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
        int materialCodeIndex = findRequiredColumnIndex(headers, "materialCode", "material_code", "原料代號", "原料編號", "代號");
        int materialNameIndex = findRequiredColumnIndex(headers, "materialName", "material_name", "原料名稱", "名稱");
        int baseUnitIndex = findRequiredColumnIndex(headers, "baseUnit", "base_unit", "基準單位", "單位");
        Integer activeIndex = findOptionalColumnIndex(headers, "active", "啟用", "狀態");
        Integer descriptionIndex = findOptionalColumnIndex(headers, "description", "說明", "備註");
        return new ColumnIndexes(materialCodeIndex, materialNameIndex, baseUnitIndex, activeIndex, descriptionIndex);
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

    private MaterialResponse toResponse(Material material) {
        return new MaterialResponse(
            material.getId(),
            material.getMaterialCode(),
            material.getMaterialName(),
            material.getBaseUnit(),
            material.isActive(),
            material.getDescription()
        );
    }

    private String safeFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename == null || originalFilename.isBlank() ? "upload" : originalFilename;
    }

    private record ImportRow(String materialCode, String materialName, String baseUnit, Boolean active, String description, int lineNumber) {
    }

    private record ColumnIndexes(int materialCodeIndex, int materialNameIndex, int baseUnitIndex, Integer activeIndex, Integer descriptionIndex) {
    }
}
