package com.yssm.yssmRecipe.service.procurement;

import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.MaterialInventorySnapshot;
import com.yssm.yssmRecipe.domain.recipe.MaterialRequirement;
import com.yssm.yssmRecipe.domain.recipe.ProductionPlan;
import com.yssm.yssmRecipe.domain.recipe.PurchaseSuggestionAnalysis;
import com.yssm.yssmRecipe.domain.recipe.PurchaseSuggestionItem;
import com.yssm.yssmRecipe.domain.recipe.PurchaseSuggestionItemSource;
import com.yssm.yssmRecipe.dto.procurement.PurchaseSuggestionItemResponse;
import com.yssm.yssmRecipe.dto.procurement.PurchaseSuggestionResponse;
import com.yssm.yssmRecipe.dto.procurement.PurchaseSuggestionSourceResponse;
import com.yssm.yssmRecipe.exception.NotFoundException;
import com.yssm.yssmRecipe.repository.MaterialInventorySnapshotRepository;
import com.yssm.yssmRecipe.repository.PurchaseSuggestionAnalysisRepository;
import com.yssm.yssmRecipe.repository.ProductionPlanRepository;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseSuggestionService {

    private final ProductionPlanRepository productionPlanRepository;
    private final MaterialInventorySnapshotRepository materialInventorySnapshotRepository;
    private final PurchaseSuggestionAnalysisRepository purchaseSuggestionAnalysisRepository;

    @Transactional
    public PurchaseSuggestionResponse generate() {
        List<ProductionPlan> productionPlans = findCurrentProductionPlans();

        Map<Long, MaterialAggregate> aggregates = new LinkedHashMap<>();
        for (ProductionPlan plan : productionPlans) {
            for (MaterialRequirement requirement : plan.getMaterialRequirements()) {
                Material material = requirement.getMaterial();
                MaterialAggregate aggregate = aggregates.computeIfAbsent(material.getId(), id -> new MaterialAggregate(material));
                aggregate.requiredWeightG = aggregate.requiredWeightG.add(requirement.getRequiredWeightG());
                aggregate.sources.add(new SourceDraft(plan, requirement));
            }
        }

        PurchaseSuggestionAnalysis analysis = new PurchaseSuggestionAnalysis();
        analysis.setItems(new ArrayList<>());

        BigDecimal totalRequired = BigDecimal.ZERO;
        BigDecimal totalStock = BigDecimal.ZERO;
        BigDecimal totalShortage = BigDecimal.ZERO;

        List<MaterialAggregate> orderedAggregates = aggregates.values().stream()
            .sorted(Comparator.comparing(aggregate -> aggregate.material.getMaterialCode()))
            .toList();

        for (MaterialAggregate aggregate : orderedAggregates) {
            MaterialInventorySnapshot snapshot = materialInventorySnapshotRepository
                .findTopByMaterialIdOrderByImportedAtDescIdDesc(aggregate.material.getId())
                .orElse(null);

            BigDecimal stockWeight = snapshot == null ? BigDecimal.ZERO : snapshot.getStockQuantity();
            BigDecimal shortageWeight = aggregate.requiredWeightG.subtract(stockWeight).max(BigDecimal.ZERO);

            PurchaseSuggestionItem item = new PurchaseSuggestionItem();
            item.setAnalysis(analysis);
            item.setMaterial(aggregate.material);
            item.setMaterialCode(aggregate.material.getMaterialCode());
            item.setMaterialName(aggregate.material.getMaterialName());
            item.setRequiredWeightG(aggregate.requiredWeightG);
            item.setStockWeightG(stockWeight);
            item.setShortageWeightG(shortageWeight);
            item.setPurchaseSuggestionWeightG(aggregate.requiredWeightG);
            item.setInventoryAvailable(snapshot != null);
            if (snapshot != null) {
                item.setInventoryImportedAt(snapshot.getImportedAt());
                item.setInventorySourceFileName(snapshot.getSourceFileName());
            }

            for (SourceDraft sourceDraft : aggregate.sources) {
                item.getSources().add(new PurchaseSuggestionItemSource(item, sourceDraft.productionPlan, sourceDraft.materialRequirement));
            }

            analysis.getItems().add(item);
            totalRequired = totalRequired.add(aggregate.requiredWeightG);
            totalStock = totalStock.add(stockWeight);
            totalShortage = totalShortage.add(shortageWeight);
        }

        analysis.setTotalRequiredWeightG(totalRequired);
        analysis.setTotalStockWeightG(totalStock);
        analysis.setTotalShortageWeightG(totalShortage);

        PurchaseSuggestionAnalysis saved = purchaseSuggestionAnalysisRepository.save(analysis);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PurchaseSuggestionResponse> list() {
        return purchaseSuggestionAnalysisRepository.findAllWithDetailsOrderByGeneratedAtDescIdDesc()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseSuggestionResponse latest() {
        PurchaseSuggestionAnalysis analysis = purchaseSuggestionAnalysisRepository.findTopByOrderByGeneratedAtDescIdDesc()
            .orElseThrow(() -> new NotFoundException("尚無請購建議分析"));
        return toResponse(findWithDetails(analysis.getId()));
    }

    @Transactional(readOnly = true)
    public PurchaseSuggestionResponse get(Long id) {
        return toResponse(findWithDetails(id));
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(Long id) {
        PurchaseSuggestionAnalysis analysis = findWithDetails(id);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeSummarySheet(workbook, analysis);
            writeSourceSheet(workbook, analysis);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("匯出請購建議 Excel 失敗", ex);
        }
    }

    private PurchaseSuggestionAnalysis findWithDetails(Long id) {
        return purchaseSuggestionAnalysisRepository.findByIdWithDetails(id)
            .orElseThrow(() -> new NotFoundException("找不到請購建議分析: " + id));
    }

    private List<ProductionPlan> findCurrentProductionPlans() {
        return productionPlanRepository.findFirstByPlanningBatchKeyIsNotNullOrderByCreatedAtDescIdDesc()
            .map(ProductionPlan::getPlanningBatchKey)
            .map(productionPlanRepository::findByPlanningBatchKeyWithDetails)
            .orElseGet(productionPlanRepository::findAllWithDetailsOrderByCreatedAtDescIdDesc);
    }

    private PurchaseSuggestionResponse toResponse(PurchaseSuggestionAnalysis analysis) {
        List<PurchaseSuggestionItemResponse> items = analysis.getItems().stream()
            .sorted(Comparator.comparing(PurchaseSuggestionItem::getMaterialCode))
            .map(item -> new PurchaseSuggestionItemResponse(
                item.getId(),
                item.getMaterial().getId(),
                item.getMaterialCode(),
                item.getMaterialName(),
                item.getRequiredWeightG(),
                item.getStockWeightG(),
                item.getShortageWeightG(),
                item.getPurchaseSuggestionWeightG(),
                item.isInventoryAvailable(),
                item.getInventoryImportedAt(),
                item.getInventorySourceFileName(),
                item.getSources().stream()
                    .map(source -> new PurchaseSuggestionSourceResponse(
                        source.getProductionPlan().getId(),
                        source.getMaterialRequirement().getId(),
                        source.getProductId(),
                        source.getProductCode(),
                        source.getProductName(),
                        source.getRecipeId(),
                        source.getRecipeCode(),
                        source.getRecipeName(),
                        source.getRecipeVersionId(),
                        source.getRecipeVersionDate(),
                        source.getPlannedQuantity(),
                        source.getCalculationMode(),
                        source.getCalculationWeightG(),
                        source.getRequiredWeightG()
                    ))
                    .toList()
            ))
            .toList();

        return new PurchaseSuggestionResponse(
            analysis.getId(),
            analysis.getGeneratedAt(),
            analysis.getTotalRequiredWeightG(),
            analysis.getTotalStockWeightG(),
            analysis.getTotalShortageWeightG(),
            items
        );
    }

    private void writeSummarySheet(Workbook workbook, PurchaseSuggestionAnalysis analysis) {
        Sheet sheet = workbook.createSheet("總原料需求");
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("總原料需求分析");
        title.createCell(1).setCellValue("分析編號");
        title.createCell(2).setCellValue(analysis.getId());
        title.createCell(3).setCellValue("產生時間");
        title.createCell(4).setCellValue(String.valueOf(analysis.getGeneratedAt()));

        Row summary = sheet.createRow(2);
        summary.createCell(0).setCellValue("總需求重量(g)");
        summary.createCell(1).setCellValue(analysis.getTotalRequiredWeightG().doubleValue());
        summary.createCell(2).setCellValue("總庫存重量(g)");
        summary.createCell(3).setCellValue(analysis.getTotalStockWeightG().doubleValue());
        summary.createCell(4).setCellValue("總缺料重量(g)");
        summary.createCell(5).setCellValue(analysis.getTotalShortageWeightG().doubleValue());

        Row header = sheet.createRow(4);
        String[] headers = {
            "原料代號", "原料名稱", "需求重量(g)", "庫存重量(g)", "總原料需求(g)", "缺料重量(g)", "是否有庫存資料", "庫存匯入時間", "來源檔名"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 5;
        for (PurchaseSuggestionItem item : analysis.getItems().stream().sorted(Comparator.comparing(PurchaseSuggestionItem::getMaterialCode)).toList()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.getMaterialCode());
            row.createCell(1).setCellValue(item.getMaterialName());
            row.createCell(2).setCellValue(item.getRequiredWeightG().doubleValue());
            row.createCell(3).setCellValue(item.getStockWeightG().doubleValue());
            row.createCell(4).setCellValue(item.getPurchaseSuggestionWeightG().doubleValue());
            row.createCell(5).setCellValue(item.getShortageWeightG().doubleValue());
            row.createCell(6).setCellValue(item.isInventoryAvailable() ? "是" : "否");
            row.createCell(7).setCellValue(item.getInventoryImportedAt() == null ? "" : String.valueOf(item.getInventoryImportedAt()));
            row.createCell(8).setCellValue(item.getInventorySourceFileName() == null ? "" : item.getInventorySourceFileName());
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeSourceSheet(Workbook workbook, PurchaseSuggestionAnalysis analysis) {
        Sheet sheet = workbook.createSheet("來源明細");
        CellStyle headerStyle = createHeaderStyle(workbook);

        Row header = sheet.createRow(0);
        String[] headers = {
            "原料代號", "原料名稱", "商品代號", "商品名稱", "配方代號", "配方名稱", "版本日期", "排程數量",
            "計算模式", "計算重量(g)", "原料需求(g)", "生產計畫ID", "原料需求ID"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (PurchaseSuggestionItem item : analysis.getItems()) {
            for (PurchaseSuggestionItemSource source : item.getSources()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.getMaterialCode());
                row.createCell(1).setCellValue(item.getMaterialName());
                row.createCell(2).setCellValue(source.getProductCode());
                row.createCell(3).setCellValue(source.getProductName());
                row.createCell(4).setCellValue(source.getRecipeCode());
                row.createCell(5).setCellValue(source.getRecipeName());
                row.createCell(6).setCellValue(String.valueOf(source.getRecipeVersionDate()));
                row.createCell(7).setCellValue(source.getPlannedQuantity());
                row.createCell(8).setCellValue(source.getCalculationMode());
                row.createCell(9).setCellValue(source.getCalculationWeightG().doubleValue());
                row.createCell(10).setCellValue(source.getRequiredWeightG().doubleValue());
                row.createCell(11).setCellValue(source.getProductionPlan().getId());
                row.createCell(12).setCellValue(source.getMaterialRequirement().getId());
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
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

    private record SourceDraft(ProductionPlan productionPlan, MaterialRequirement materialRequirement) {
    }

    private static final class MaterialAggregate {
        private final Material material;
        private BigDecimal requiredWeightG = BigDecimal.ZERO;
        private final List<SourceDraft> sources = new ArrayList<>();

        private MaterialAggregate(Material material) {
            this.material = material;
        }
    }
}
