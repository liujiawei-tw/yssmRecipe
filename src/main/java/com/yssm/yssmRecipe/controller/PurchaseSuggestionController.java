package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.procurement.PurchaseSuggestionResponse;
import com.yssm.yssmRecipe.service.procurement.PurchaseSuggestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/purchase-suggestions")
public class PurchaseSuggestionController {

    private final PurchaseSuggestionService purchaseSuggestionService;

    @PostMapping
    public PurchaseSuggestionResponse generate() {
        return purchaseSuggestionService.generate();
    }

    @GetMapping
    public List<PurchaseSuggestionResponse> list() {
        return purchaseSuggestionService.list();
    }

    @GetMapping("/latest")
    public PurchaseSuggestionResponse latest() {
        return purchaseSuggestionService.latest();
    }

    @GetMapping("/{id}")
    public PurchaseSuggestionResponse get(@PathVariable Long id) {
        return purchaseSuggestionService.get(id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        byte[] content = purchaseSuggestionService.exportExcel(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=purchase-suggestion-" + id + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .contentLength(content.length)
            .body(content);
    }
}
