package com.yssm.yssmRecipe.controller;

import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemDetailResponse;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemImportResult;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemUpsertRequest;
import com.yssm.yssmRecipe.service.recipe.RecipeVersionItemService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/recipe-version-items")
public class RecipeVersionItemController {

    private final RecipeVersionItemService recipeVersionItemService;

    @GetMapping
    public List<RecipeVersionItemDetailResponse> list(
        @RequestParam(required = false) Long recipeId,
        @RequestParam(required = false) Long recipeVersionId,
        @RequestParam(required = false) Long materialId,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String recipeSearch,
        @RequestParam(required = false) String versionSearch,
        @RequestParam(required = false) String materialSearch
    ) {
        return recipeVersionItemService.list(recipeId, recipeVersionId, materialId, search, recipeSearch, versionSearch, materialSearch);
    }

    @GetMapping("/{id}")
    public RecipeVersionItemDetailResponse get(@PathVariable Long id) {
        return recipeVersionItemService.get(id);
    }

    @PostMapping
    public RecipeVersionItemDetailResponse create(@Valid @RequestBody RecipeVersionItemUpsertRequest request) {
        return recipeVersionItemService.create(request);
    }

    @PutMapping("/{id}")
    public RecipeVersionItemDetailResponse update(@PathVariable Long id, @Valid @RequestBody RecipeVersionItemUpsertRequest request) {
        return recipeVersionItemService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recipeVersionItemService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
        @RequestParam(required = false) Long recipeId,
        @RequestParam(required = false) Long recipeVersionId,
        @RequestParam(required = false) Long materialId,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String recipeSearch,
        @RequestParam(required = false) String versionSearch,
        @RequestParam(required = false) String materialSearch
    ) {
        return recipeVersionItemService.export(recipeId, recipeVersionId, materialId, search, recipeSearch, versionSearch, materialSearch);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecipeVersionItemImportResult importExcel(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return recipeVersionItemService.importExcel(file);
    }
}
