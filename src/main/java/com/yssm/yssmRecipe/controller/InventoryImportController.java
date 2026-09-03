package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.inventory.InventoryImportResponse;
import com.yssm.yssmRecipe.service.inventory.InventoryImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory-imports")
public class InventoryImportController {

    private final InventoryImportService inventoryImportService;

    @PostMapping(value = "/product-stock", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InventoryImportResponse importProductStock(@RequestPart("file") MultipartFile file) {
        return inventoryImportService.importProductStock(file);
    }

    @PostMapping(value = "/material-stock", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InventoryImportResponse importMaterialStock(@RequestPart("file") MultipartFile file) {
        return inventoryImportService.importMaterialStock(file);
    }
}
