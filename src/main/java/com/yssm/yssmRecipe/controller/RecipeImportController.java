package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.recipe.RecipeImportResponse;
import com.yssm.yssmRecipe.service.recipe.RecipeImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class RecipeImportController {

    private final RecipeImportService recipeImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecipeImportResponse importExcel(@RequestPart("file") MultipartFile file) {
        return recipeImportService.importFromExcel(file);
    }
}
