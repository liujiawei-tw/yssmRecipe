package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.product.ProductImportResponse;
import com.yssm.yssmRecipe.service.product.ProductImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductImportController {

    private final ProductImportService productImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImportResponse importExcel(@RequestPart("file") MultipartFile file) {
        return productImportService.importFromExcel(file);
    }
}
