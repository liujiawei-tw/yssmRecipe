package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.recipe.RecipeVersionResponse;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionUpsertRequest;
import com.yssm.yssmRecipe.service.recipe.RecipeVersionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RecipeVersionController {

    private final RecipeVersionService recipeVersionService;

    @GetMapping("/recipes/{recipeId}/versions")
    public List<RecipeVersionResponse> list(@PathVariable Long recipeId) {
        return recipeVersionService.listByRecipeId(recipeId);
    }

    @GetMapping("/recipes/{recipeId}/versions/latest")
    public RecipeVersionResponse latest(@PathVariable Long recipeId) {
        return recipeVersionService.getLatestByRecipeId(recipeId);
    }

    @GetMapping("/recipe-versions/{id}")
    public RecipeVersionResponse get(@PathVariable Long id) {
        return recipeVersionService.get(id);
    }

    @PostMapping("/recipes/{recipeId}/versions")
    public RecipeVersionResponse create(@PathVariable Long recipeId, @Valid @RequestBody RecipeVersionUpsertRequest request) {
        return recipeVersionService.create(recipeId, request);
    }

    @PutMapping("/recipe-versions/{id}")
    public RecipeVersionResponse update(
        @PathVariable Long id,
        @RequestParam(defaultValue = "false") boolean force,
        @Valid @RequestBody RecipeVersionUpsertRequest request
    ) {
        return recipeVersionService.update(id, request, force);
    }

    @DeleteMapping("/recipe-versions/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recipeVersionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recipe-versions/{id}/clone")
    public RecipeVersionResponse cloneVersion(@PathVariable Long id, @Valid @RequestBody RecipeVersionUpsertRequest request) {
        return recipeVersionService.cloneVersion(id, request);
    }
}
