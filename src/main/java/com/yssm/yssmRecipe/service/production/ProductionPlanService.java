package com.yssm.yssmRecipe.service.production;

import com.yssm.yssmRecipe.domain.recipe.CalculationMode;
import com.yssm.yssmRecipe.domain.recipe.MaterialRequirement;
import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.domain.recipe.ProductPackaging;
import com.yssm.yssmRecipe.domain.recipe.ProductRecipeMapping;
import com.yssm.yssmRecipe.domain.recipe.ProductStockSnapshot;
import com.yssm.yssmRecipe.domain.recipe.ProductionPlan;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.dto.production.MaterialRequirementResponse;
import com.yssm.yssmRecipe.dto.production.ProductionPlanRequest;
import com.yssm.yssmRecipe.dto.production.ProductionPlanResponse;
import com.yssm.yssmRecipe.exception.NotFoundException;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.ProductRepository;
import com.yssm.yssmRecipe.repository.ProductStockSnapshotRepository;
import com.yssm.yssmRecipe.repository.ProductionPlanRepository;
import com.yssm.yssmRecipe.service.product.ProductService;
import com.yssm.yssmRecipe.service.recipe.RecipeCalculationService;
import com.yssm.yssmRecipe.service.recipe.RecipeVersionService;
import com.yssm.yssmRecipe.service.recipe.RecipeCalculationResult;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionPlanService {

    private final ProductRepository productRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final ProductStockSnapshotRepository productStockSnapshotRepository;
    private final MaterialRepository materialRepository;
    private final ProductService productService;
    private final RecipeVersionService recipeVersionService;
    private final RecipeCalculationService recipeCalculationService;

    @Transactional
    public ProductionPlanResponse create(ProductionPlanRequest request) {
        Product product = productRepository.findById(request.productId())
            .orElseThrow(() -> new NotFoundException("找不到商品: " + request.productId()));

        Integer currentStock = request.currentStock();
        if (currentStock == null) {
            currentStock = productStockSnapshotRepository.findTopByProductIdOrderByImportedAtDescIdDesc(product.getId())
                .map(snapshot -> snapshot.getStockQuantity())
                .orElseThrow(() -> new IllegalArgumentException("請先匯入商品庫存或提供 currentStock"));
        }

        int suggestedQuantity = Math.max(product.getMaxStock() - currentStock, 0);
        int plannedQuantity = request.plannedQuantity() == null ? suggestedQuantity : request.plannedQuantity();
        CalculationMode calculationMode = request.calculationMode() == null
            ? (request.plannedQuantity() == null ? CalculationMode.SYSTEM : CalculationMode.MANUAL)
            : request.calculationMode();

        return createPlan(product, currentStock, suggestedQuantity, plannedQuantity, calculationMode, null);
    }

    private ProductionPlanResponse createPlan(Product product, Integer currentStock, int suggestedQuantity, int plannedQuantity, CalculationMode calculationMode, String planningBatchKey) {
        ProductPackaging packaging = productService.findActivePackaging(product.getId());
        ProductRecipeMapping mapping = productService.findPrimaryRecipeMapping(product.getId());

        BigDecimal calculationWeightG = packaging.getGramWeightPerErpUnit().multiply(BigDecimal.valueOf(plannedQuantity));
        RecipeVersion recipeVersion = recipeVersionService.findLatestEntityByRecipeId(mapping.getRecipe().getId());

        ProductionPlan plan = new ProductionPlan();
        plan.setProduct(product);
        plan.setCurrentStock(currentStock);
        plan.setSafetyStock(product.getSafetyStock());
        plan.setTargetStock(product.getMaxStock());
        plan.setSuggestedQuantity(suggestedQuantity);
        plan.setPlannedQuantity(plannedQuantity);
        plan.setCalculationMode(calculationMode);
        plan.setGramWeightPerErpUnit(packaging.getGramWeightPerErpUnit());
        plan.setCalculationWeightG(calculationWeightG);
        plan.setRecipeVersion(recipeVersion);
        plan.setPlanningBatchKey(planningBatchKey);

        List<MaterialRequirement> requirements = new ArrayList<>();
        if (calculationWeightG.compareTo(BigDecimal.ZERO) > 0) {
            RecipeCalculationResult calculationResult = recipeCalculationService.calculate(recipeVersion, calculationWeightG);
            for (var item : calculationResult.items()) {
                MaterialRequirement requirement = new MaterialRequirement(
                    plan,
                    materialRepository.findByMaterialCode(item.materialCode())
                        .orElseThrow(() -> new NotFoundException("找不到原料: " + item.materialCode())),
                    recipeVersion,
                    item.ratio(),
                    item.percentage(),
                    item.actualWeightG()
                );
                requirements.add(requirement);
            }
        }
        plan.setMaterialRequirements(requirements);
        ProductionPlan saved = productionPlanRepository.save(plan);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductionPlanResponse get(Long id) {
        ProductionPlan plan = productionPlanRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到生產需求: " + id));
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<ProductionPlanResponse> list() {
        return productionPlanRepository.findAllWithDetailsOrderByCreatedAtDescIdDesc()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductionPlanResponse> latest() {
        return productionPlanRepository.findFirstByPlanningBatchKeyIsNotNullOrderByCreatedAtDescIdDesc()
            .map(ProductionPlan::getPlanningBatchKey)
            .map(productionPlanRepository::findByPlanningBatchKeyWithDetails)
            .orElseGet(productionPlanRepository::findAllWithDetailsOrderByCreatedAtDescIdDesc)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public List<ProductionPlanResponse> refreshFromLatestInventory() {
        List<ProductionPlanResponse> refreshedPlans = new ArrayList<>();
        String planningBatchKey = UUID.randomUUID().toString();
        for (Product product : productRepository.findAll()) {
            if (!product.isActive()) {
                continue;
            }
            if (productStockSnapshotRepository.findTopByProductIdOrderByImportedAtDescIdDesc(product.getId()).isEmpty()) {
                continue;
            }
            try {
                int currentStock = productStockSnapshotRepository.findTopByProductIdOrderByImportedAtDescIdDesc(product.getId())
                    .orElseThrow()
                    .getStockQuantity();
                int suggestedQuantity = Math.max(product.getMaxStock() - currentStock, 0);
                refreshedPlans.add(createPlan(product, currentStock, suggestedQuantity, suggestedQuantity, CalculationMode.SYSTEM, planningBatchKey));
            } catch (RuntimeException ex) {
                log.warn("商品 {} 缺少完整生產設定，略過生產計畫更新", product.getProductCode(), ex);
            }
        }
        return refreshedPlans;
    }

    @Transactional
    public List<ProductionPlanResponse> refreshFromProductStockSnapshots(List<ProductStockSnapshot> snapshots) {
        String planningBatchKey = UUID.randomUUID().toString();
        List<ProductionPlanResponse> refreshedPlans = new ArrayList<>();
        for (ProductStockSnapshot snapshot : snapshots) {
            Product product = snapshot.getProduct();
            if (!product.isActive()) {
                continue;
            }
            try {
                int currentStock = snapshot.getStockQuantity();
                int suggestedQuantity = Math.max(product.getMaxStock() - currentStock, 0);
                refreshedPlans.add(createPlan(product, currentStock, suggestedQuantity, suggestedQuantity, CalculationMode.SYSTEM, planningBatchKey));
            } catch (RuntimeException ex) {
                log.warn("商品 {} 缺少完整生產設定，略過生產計畫更新", product.getProductCode(), ex);
            }
        }
        return refreshedPlans;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportExcel() {
        List<ProductionPlanResponse> plans = list();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writePlanSheet(workbook, plans);
            writeDetailSheet(workbook, plans);
            workbook.write(outputStream);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=production-plans.xlsx")
                .body(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("匯出生產計畫 Excel 失敗", ex);
        }
    }

    private void writePlanSheet(Workbook workbook, List<ProductionPlanResponse> plans) {
        Sheet sheet = workbook.createSheet("生產計畫清單");
        CellStyle headerStyle = createHeaderStyle(workbook);

        String[] headers = {
            "生產計畫ID",
            "商品代號",
            "商品名稱",
            "目前庫存",
            "安全庫存",
            "目標庫存",
            "建議量",
            "規劃量",
            "計算模式",
            "ERP 單位",
            "每單位克重(g)",
            "計算重量(g)",
            "配方名稱",
            "版本日期",
            "原料數"
        };

        Row headerRow = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (ProductionPlanResponse plan : plans) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row, 0, plan.id());
            writeCell(row, 1, plan.productCode());
            writeCell(row, 2, plan.productName());
            writeCell(row, 3, plan.currentStock());
            writeCell(row, 4, plan.safetyStock());
            writeCell(row, 5, plan.targetStock());
            writeCell(row, 6, plan.suggestedQuantity());
            writeCell(row, 7, plan.plannedQuantity());
            writeCell(row, 8, plan.calculationMode() == null ? null : plan.calculationMode().name());
            writeCell(row, 9, plan.erpUnit());
            writeCell(row, 10, plan.gramWeightPerErpUnit());
            writeCell(row, 11, plan.calculationWeightG());
            writeCell(row, 12, plan.recipeName());
            writeCell(row, 13, plan.versionDate());
            writeCell(row, 14, plan.materialRequirements() == null ? 0 : plan.materialRequirements().size());
        }

        autosize(sheet, headers.length);
    }

    private void writeDetailSheet(Workbook workbook, List<ProductionPlanResponse> plans) {
        Sheet sheet = workbook.createSheet("計畫明細");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle percentageStyle = createPercentageStyle(workbook);

        String[] headers = {
            "生產計畫ID",
            "商品代號",
            "商品名稱",
            "配方名稱",
            "版本日期",
            "規劃量",
            "計算模式",
            "原料代號",
            "原料名稱",
            "比例",
            "百分比",
            "需求重量(g)"
        };

        Row headerRow = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (ProductionPlanResponse plan : plans) {
            for (MaterialRequirementResponse requirement : plan.materialRequirements()) {
                Row row = sheet.createRow(rowIndex++);
                writeCell(row, 0, plan.id());
                writeCell(row, 1, plan.productCode());
                writeCell(row, 2, plan.productName());
                writeCell(row, 3, plan.recipeName());
                writeCell(row, 4, plan.versionDate());
                writeCell(row, 5, plan.plannedQuantity());
                writeCell(row, 6, plan.calculationMode() == null ? null : plan.calculationMode().name());
                writeCell(row, 7, requirement.materialCode());
                writeCell(row, 8, requirement.materialName());
                writeCell(row, 9, requirement.ratio());
                writePercentageCell(row, 10, requirement.percentage(), percentageStyle);
                writeCell(row, 11, requirement.requiredWeightG());
            }
        }

        autosize(sheet, headers.length);
    }

    private void autosize(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private void writeCell(Row row, int index, String value) {
        if (value == null) {
            row.createCell(index).setBlank();
            return;
        }
        row.createCell(index).setCellValue(value);
    }

    private void writeCell(Row row, int index, Integer value) {
        if (value == null) {
            row.createCell(index).setBlank();
            return;
        }
        row.createCell(index).setCellValue(value);
    }

    private void writeCell(Row row, int index, Long value) {
        if (value == null) {
            row.createCell(index).setBlank();
            return;
        }
        row.createCell(index).setCellValue(value);
    }

    private void writeCell(Row row, int index, BigDecimal value) {
        if (value == null) {
            row.createCell(index).setBlank();
            return;
        }
        row.createCell(index).setCellValue(value.doubleValue());
    }

    private void writePercentageCell(Row row, int index, BigDecimal value, CellStyle style) {
        if (value == null) {
            row.createCell(index).setBlank();
            return;
        }
        Cell cell = row.createCell(index);
        cell.setCellValue(value.divide(BigDecimal.valueOf(100)).doubleValue());
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor((short) 22);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createPercentageStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat("0.00%"));
        return style;
    }

    private ProductionPlanResponse toResponse(ProductionPlan plan) {
        List<MaterialRequirementResponse> materialRequirements = plan.getMaterialRequirements().stream()
            .map(requirement -> new MaterialRequirementResponse(
                requirement.getMaterial().getId(),
                requirement.getMaterial().getMaterialCode(),
                requirement.getMaterial().getMaterialName(),
                requirement.getRecipeVersion().getId(),
                requirement.getRatio(),
                requirement.getPercentage(),
                requirement.getRequiredWeightG()
            ))
            .toList();

        return new ProductionPlanResponse(
            plan.getId(),
            plan.getProduct().getId(),
            plan.getProduct().getProductCode(),
            plan.getProduct().getProductName(),
            plan.getCurrentStock(),
            plan.getSafetyStock(),
            plan.getTargetStock(),
            plan.getSuggestedQuantity(),
            plan.getPlannedQuantity(),
            plan.getCalculationMode(),
            plan.getProduct().getErpUnit(),
            plan.getGramWeightPerErpUnit(),
            plan.getCalculationWeightG(),
            plan.getRecipeVersion().getId(),
            plan.getRecipeVersion().getRecipe().getRecipeName(),
            plan.getRecipeVersion().getVersionDate().toString(),
            materialRequirements
        );
    }
}
