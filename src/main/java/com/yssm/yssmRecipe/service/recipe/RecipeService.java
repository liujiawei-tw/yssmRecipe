package com.yssm.yssmRecipe.service.recipe;

import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.dto.recipe.RecipeResponse;
import com.yssm.yssmRecipe.dto.recipe.RecipeUpsertRequest;
import com.yssm.yssmRecipe.exception.NotFoundException;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;

    @Transactional(readOnly = true)
    public List<RecipeResponse> list() {
        return recipeRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RecipeResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public RecipeResponse create(RecipeUpsertRequest request) {
        recipeRepository.findByRecipeCode(request.recipeCode()).ifPresent(existing -> {
            throw new IllegalArgumentException("recipeCode 已存在");
        });
        Recipe recipe = new Recipe(request.recipeCode(), request.recipeName());
        recipe.setActive(request.active() == null || request.active());
        recipe.setDescription(request.description());
        return toResponse(recipeRepository.save(recipe));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public RecipeResponse update(Long id, RecipeUpsertRequest request) {
        Recipe recipe = find(id);
        if (!recipe.getRecipeCode().equals(request.recipeCode())) {
            recipeRepository.findByRecipeCode(request.recipeCode()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("recipeCode 已存在");
                }
            });
        }
        recipe.setRecipeCode(request.recipeCode());
        recipe.setRecipeName(request.recipeName());
        recipe.setActive(request.active() == null || request.active());
        recipe.setDescription(request.description());
        return toResponse(recipeRepository.save(recipe));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public void delete(Long id) {
        Recipe recipe = find(id);
        try {
            recipeRepository.delete(recipe);
            recipeRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("配方仍被歷史資料或明細引用，無法刪除", ex);
        }
    }

    public Recipe find(Long id) {
        return recipeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到配方: " + id));
    }

    private RecipeResponse toResponse(Recipe recipe) {
        return new RecipeResponse(
            recipe.getId(),
            recipe.getRecipeCode(),
            recipe.getRecipeName(),
            recipe.isActive(),
            recipe.getDescription()
        );
    }
}
