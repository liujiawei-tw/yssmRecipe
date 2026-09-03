package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.recipe.RecipeResponse;
import com.yssm.yssmRecipe.dto.recipe.RecipeUpsertRequest;
import com.yssm.yssmRecipe.service.recipe.RecipeService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public List<RecipeResponse> list() {
        return recipeService.list();
    }

    @GetMapping("/{id}")
    public RecipeResponse get(@PathVariable Long id) {
        return recipeService.get(id);
    }

    @PostMapping
    public RecipeResponse create(@Valid @RequestBody RecipeUpsertRequest request) {
        return recipeService.create(request);
    }

    @PutMapping("/{id}")
    public RecipeResponse update(@PathVariable Long id, @Valid @RequestBody RecipeUpsertRequest request) {
        return recipeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recipeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
