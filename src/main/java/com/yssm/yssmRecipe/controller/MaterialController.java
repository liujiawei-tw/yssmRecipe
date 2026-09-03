package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.material.MaterialResponse;
import com.yssm.yssmRecipe.dto.material.MaterialImportResponse;
import com.yssm.yssmRecipe.dto.material.MaterialUpsertRequest;
import com.yssm.yssmRecipe.service.material.MaterialService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    public List<MaterialResponse> list() {
        return materialService.list();
    }

    @GetMapping("/{id}")
    public MaterialResponse get(@PathVariable Long id) {
        return materialService.get(id);
    }

    @PostMapping
    public MaterialResponse create(@Valid @RequestBody MaterialUpsertRequest request) {
        return materialService.create(request);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialImportResponse importExcel(@RequestPart("file") MultipartFile file) {
        return materialService.importFromExcel(file);
    }

    @PutMapping("/{id}")
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody MaterialUpsertRequest request) {
        return materialService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
