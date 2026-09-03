package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.service.recipe.RecipeCalculationRequest;
import com.yssm.yssmRecipe.service.recipe.RecipeCalculationResult;
import com.yssm.yssmRecipe.service.recipe.RecipeCalculationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipe-calculations")
public class RecipeCalculationController {

    private final RecipeCalculationService recipeCalculationService;

    @PostMapping
    public ResponseEntity<RecipeCalculationResult> calculate(@Valid @RequestBody RecipeCalculationRequest request) {
        if (request.recipeVersionId() != null) {
            return ResponseEntity.ok(recipeCalculationService.calculateByVersionId(request.recipeVersionId(), request.targetWeightG()));
        }
        if (request.recipeId() != null) {
            return ResponseEntity.ok(recipeCalculationService.calculateLatest(request.recipeId(), request.targetWeightG()));
        }
        throw new IllegalArgumentException("recipeId 或 recipeVersionId 至少需提供一個");
    }
}
