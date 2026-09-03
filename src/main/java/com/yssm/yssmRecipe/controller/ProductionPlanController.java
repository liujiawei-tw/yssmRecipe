package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.production.ProductionPlanRequest;
import com.yssm.yssmRecipe.dto.production.ProductionPlanResponse;
import com.yssm.yssmRecipe.service.production.ProductionPlanService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/production-plans")
public class ProductionPlanController {

    private final ProductionPlanService productionPlanService;

    @GetMapping
    public List<ProductionPlanResponse> list() {
        return productionPlanService.list();
    }

    @GetMapping("/latest")
    public List<ProductionPlanResponse> latest() {
        return productionPlanService.latest();
    }

    @PostMapping("/refresh-from-inventory")
    public List<ProductionPlanResponse> refreshFromLatestInventory() {
        return productionPlanService.refreshFromLatestInventory();
    }

    @PostMapping
    public ProductionPlanResponse create(@Valid @RequestBody ProductionPlanRequest request) {
        return productionPlanService.create(request);
    }

    @GetMapping("/{id:\\d+}")
    public ProductionPlanResponse get(@PathVariable Long id) {
        return productionPlanService.get(id);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return productionPlanService.exportExcel();
    }
}
