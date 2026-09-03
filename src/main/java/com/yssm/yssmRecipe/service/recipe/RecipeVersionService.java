package com.yssm.yssmRecipe.service.recipe;

import com.yssm.yssmRecipe.domain.recipe.Material;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersion;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionStatus;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemRequest;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemResponse;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionResponse;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionUpsertRequest;
import com.yssm.yssmRecipe.exception.NotFoundException;
import com.yssm.yssmRecipe.repository.MaterialRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import com.yssm.yssmRecipe.repository.RecipeVersionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeVersionService {

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final MaterialRepository materialRepository;

    @Transactional(readOnly = true)
    public List<RecipeVersionResponse> listByRecipeId(Long recipeId) {
        return recipeVersionRepository.findByRecipeIdOrderByVersionDateDescIdDesc(recipeId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RecipeVersionResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public RecipeVersionResponse getLatestByRecipeId(Long recipeId) {
        return recipeVersionRepository.findTopByRecipeIdAndStatusOrderByVersionDateDescIdDesc(recipeId, RecipeVersionStatus.ACTIVE)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("找不到有效配方版本"));
    }

    @Transactional(readOnly = true)
    public RecipeVersion findLatestEntityByRecipeId(Long recipeId) {
        return recipeVersionRepository.findTopByRecipeIdAndStatusOrderByVersionDateDescIdDesc(recipeId, RecipeVersionStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("找不到有效配方版本"));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public RecipeVersionResponse create(Long recipeId, RecipeVersionUpsertRequest request) {
        Recipe recipe = findRecipe(recipeId);
        RecipeVersion version = new RecipeVersion(recipe, request.versionDate(), request.baseWeightG(), RecipeVersionStatus.valueOf(request.status()));
        version.setCreatedBy(request.createdBy());
        version.setItems(buildItems(version, request.items()));
        RecipeVersion saved = recipeVersionRepository.save(version);
        ensureSingleActiveVersion(saved.getRecipe().getId(), saved.getId(), saved.getStatus());
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public RecipeVersionResponse update(Long id, RecipeVersionUpsertRequest request) {
        RecipeVersion version = find(id);
        version.setVersionDate(request.versionDate());
        version.setBaseWeightG(request.baseWeightG());
        version.setStatus(RecipeVersionStatus.valueOf(request.status()));
        version.setCreatedBy(request.createdBy());
        updateItems(version, request.items());
        RecipeVersion saved = recipeVersionRepository.save(version);
        ensureSingleActiveVersion(saved.getRecipe().getId(), saved.getId(), saved.getStatus());
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public void delete(Long id) {
        recipeVersionRepository.delete(find(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "recipeVersionItemSearch", allEntries = true)
    public RecipeVersionResponse cloneVersion(Long id, RecipeVersionUpsertRequest request) {
        RecipeVersion source = find(id);
        RecipeVersion clone = new RecipeVersion(source.getRecipe(), request.versionDate(), request.baseWeightG(), RecipeVersionStatus.valueOf(request.status()));
        clone.setCreatedBy(request.createdBy());
        List<RecipeVersionItemRequest> items = request.items();
        if (items == null || items.isEmpty()) {
            items = source.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getDisplayOrder() == null ? Integer.MAX_VALUE : item.getDisplayOrder()))
                .map(item -> new RecipeVersionItemRequest(item.getMaterial().getId(), item.getRatio(), item.getDisplayOrder()))
                .toList();
        }
        clone.setItems(buildItems(clone, items));
        RecipeVersion saved = recipeVersionRepository.save(clone);
        ensureSingleActiveVersion(saved.getRecipe().getId(), saved.getId(), saved.getStatus());
        return toResponse(saved);
    }

    public RecipeVersion find(Long id) {
        return recipeVersionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到配方版本: " + id));
    }

    public Material findMaterial(Long id) {
        return materialRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到原料: " + id));
    }

    private Recipe findRecipe(Long id) {
        return recipeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到配方: " + id));
    }

    private List<RecipeVersionItem> buildItems(RecipeVersion version, List<RecipeVersionItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("配方版本至少需要一筆原料");
        }
        List<RecipeVersionItem> items = new ArrayList<>();
        for (RecipeVersionItemRequest request : requests) {
            Material material = findMaterial(request.materialId());
            items.add(new RecipeVersionItem(version, material, request.ratio(), request.displayOrder()));
        }
        return items;
    }

    private void updateItems(RecipeVersion version, List<RecipeVersionItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("配方版本至少需要一筆原料");
        }

        List<RecipeVersionItem> existingItems = version.getItems().stream()
            .sorted(Comparator.comparing(item -> item.getDisplayOrder() == null ? Integer.MAX_VALUE : item.getDisplayOrder()))
            .toList();
        if (existingItems.size() != requests.size()) {
            throw new IllegalArgumentException("版本編輯不可刪除原料明細，請到配方版本項目頁處理");
        }

        Set<Long> materialIds = new HashSet<>();
        Set<Integer> displayOrders = new HashSet<>();
        for (int index = 0; index < requests.size(); index++) {
            RecipeVersionItemRequest request = requests.get(index);
            if (!materialIds.add(request.materialId())) {
                throw new IllegalArgumentException("同一配方版本內不能重複使用相同原料");
            }
            if (!displayOrders.add(request.displayOrder())) {
                throw new IllegalArgumentException("同一配方版本內的排序不能重複");
            }
            RecipeVersionItem item = existingItems.get(index);
            item.setMaterial(findMaterial(request.materialId()));
            item.setRatio(request.ratio());
            item.setDisplayOrder(request.displayOrder());
        }
    }

    public void ensureSingleActiveVersion(Long recipeId, Long currentVersionId, RecipeVersionStatus status) {
        if (status != RecipeVersionStatus.ACTIVE) {
            return;
        }

        List<RecipeVersion> versions = recipeVersionRepository.findByRecipeIdOrderByVersionDateDescIdDesc(recipeId);
        boolean changed = false;
        for (RecipeVersion version : versions) {
            if (version.getId().equals(currentVersionId)) {
                continue;
            }
            if (version.getStatus() == RecipeVersionStatus.ACTIVE) {
                version.setStatus(RecipeVersionStatus.ARCHIVED);
                changed = true;
            }
        }
        if (changed) {
            recipeVersionRepository.saveAll(versions);
        }
    }

    private RecipeVersionResponse toResponse(RecipeVersion version) {
        return new RecipeVersionResponse(
            version.getId(),
            version.getRecipe().getId(),
            version.getRecipe().getRecipeCode(),
            version.getRecipe().getRecipeName(),
            version.getVersionDate(),
            version.getBaseWeightG(),
            version.getStatus().name(),
            version.getCreatedBy(),
            version.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getDisplayOrder() == null ? Integer.MAX_VALUE : item.getDisplayOrder()))
                .map(this::toItemResponse)
                .toList()
        );
    }

    private RecipeVersionItemResponse toItemResponse(RecipeVersionItem item) {
        return new RecipeVersionItemResponse(
            item.getId(),
            item.getMaterial().getId(),
            item.getMaterial().getMaterialCode(),
            item.getMaterial().getMaterialName(),
            item.getRatio(),
            item.getDisplayOrder()
        );
    }
}
