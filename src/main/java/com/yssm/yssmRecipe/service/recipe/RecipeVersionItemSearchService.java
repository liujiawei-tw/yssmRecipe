package com.yssm.yssmRecipe.service.recipe;

import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItem;
import com.yssm.yssmRecipe.domain.recipe.RecipeVersionItemSearchDocument;
import com.yssm.yssmRecipe.dto.recipe.RecipeVersionItemDetailResponse;
import com.yssm.yssmRecipe.repository.RecipeVersionItemRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeVersionItemSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final RecipeVersionItemRepository recipeVersionItemRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "recipeVersionItemSearch", key = "T(java.util.Objects).toString(#recipeId) + '|' + T(java.util.Objects).toString(#recipeVersionId) + '|' + T(java.util.Objects).toString(#materialId) + '|' + (#search == null ? '' : #search) + '|' + (#recipeSearch == null ? '' : #recipeSearch) + '|' + (#versionSearch == null ? '' : #versionSearch) + '|' + (#materialSearch == null ? '' : #materialSearch)")
    public List<RecipeVersionItemDetailResponse> search(
        Long recipeId,
        Long recipeVersionId,
        Long materialId,
        String search,
        String recipeSearch,
        String versionSearch,
        String materialSearch
    ) {
        if (!StringUtils.hasText(search) && !StringUtils.hasText(recipeSearch) && !StringUtils.hasText(versionSearch) && !StringUtils.hasText(materialSearch)) {
            return recipeVersionItemRepository.search(recipeId, recipeVersionId, materialId).stream()
                .map(RecipeVersionItemSearchService::toResponse)
                .toList();
        }

        return fallbackSearch(recipeId, recipeVersionId, materialId, search, recipeSearch, versionSearch, materialSearch);
    }

    @Transactional(readOnly = true)
    public void reindexAll() {
        try {
            List<RecipeVersionItemSearchDocument> documents = recipeVersionItemRepository.search(null, null, null).stream()
                .map(RecipeVersionItemSearchDocument::from)
                .toList();
            Set<String> currentIds = new HashSet<>();
            for (RecipeVersionItemSearchDocument document : documents) {
                if (document.getId() != null) {
                    currentIds.add(document.getId());
                }
            }
            try {
                CriteriaQuery existingQuery = new CriteriaQuery(Criteria.where("id").exists());
                existingQuery.setPageable(PageRequest.of(0, 10000));
                elasticsearchOperations.search(existingQuery, RecipeVersionItemSearchDocument.class).getSearchHits().stream()
                    .map(SearchHit::getId)
                    .filter(id -> !currentIds.contains(id))
                    .forEach(id -> elasticsearchOperations.delete(id, RecipeVersionItemSearchDocument.class));
            } catch (RuntimeException ignored) {
                // Ignore missing index during bootstrap. Saving the first document will create it.
            }
            documents.forEach(elasticsearchOperations::save);
        } catch (RuntimeException ex) {
            log.warn("重新建立 Elasticsearch 索引失敗", ex);
        }
    }

    private List<RecipeVersionItemDetailResponse> searchWithElasticsearch(
        Long recipeId,
        Long recipeVersionId,
        Long materialId,
        String search,
        String recipeSearch,
        String versionSearch,
        String materialSearch
    ) {
        Criteria criteria = null;

        if (recipeId != null) {
            criteria = addCriteria(criteria, Criteria.where("recipeId").is(recipeId));
        }
        if (recipeVersionId != null) {
            criteria = addCriteria(criteria, Criteria.where("recipeVersionId").is(recipeVersionId));
        }
        if (materialId != null) {
            criteria = addCriteria(criteria, Criteria.where("materialId").is(materialId));
        }

        if (StringUtils.hasText(search)) {
            String term = search.trim();
            Criteria combinedCriteria = Criteria.where("recipeCode").contains(term)
                .or("recipeName").contains(term)
                .or("versionDate").contains(term)
                .or("versionStatus").contains(term)
                .or("materialCode").contains(term)
                .or("materialName").contains(term);
            criteria = addCriteria(criteria, combinedCriteria);
        }
        if (StringUtils.hasText(recipeSearch)) {
            String term = recipeSearch.trim();
            Criteria recipeCriteria = Criteria.where("recipeCode").contains(term).or("recipeName").contains(term);
            criteria = addCriteria(criteria, recipeCriteria);
        }
        if (StringUtils.hasText(versionSearch)) {
            String term = versionSearch.trim();
            Criteria versionCriteria = Criteria.where("versionDate").contains(term).or("versionStatus").contains(term);
            criteria = addCriteria(criteria, versionCriteria);
        }
        if (StringUtils.hasText(materialSearch)) {
            String term = materialSearch.trim();
            Criteria materialCriteria = Criteria.where("materialCode").contains(term).or("materialName").contains(term);
            criteria = addCriteria(criteria, materialCriteria);
        }

        if (criteria == null) {
            return recipeVersionItemRepository.search(recipeId, recipeVersionId, materialId).stream()
                .map(RecipeVersionItemSearchService::toResponse)
                .toList();
        }

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(0, 10000));
        var hits = elasticsearchOperations.search(query, RecipeVersionItemSearchDocument.class);
        return hits.getSearchHits().stream()
            .map(SearchHit::getContent)
            .map(RecipeVersionItemSearchService::toResponse)
            .sorted(RESPONSE_COMPARATOR)
            .toList();
    }

    private List<RecipeVersionItemDetailResponse> fallbackSearch(
        Long recipeId,
        Long recipeVersionId,
        Long materialId,
        String search,
        String recipeSearch,
        String versionSearch,
        String materialSearch
    ) {
        List<RecipeVersionItem> items = recipeVersionItemRepository.search(recipeId, recipeVersionId, materialId);
        return items.stream()
            .filter(item -> matches(item, search, recipeSearch, versionSearch, materialSearch))
            .map(RecipeVersionItemSearchService::toResponse)
            .sorted(RESPONSE_COMPARATOR)
            .toList();
    }

    private boolean matches(RecipeVersionItem item, String search, String recipeSearch, String versionSearch, String materialSearch) {
        String combinedTerm = normalize(search);
        String recipeTerm = normalize(recipeSearch);
        String versionTerm = normalize(versionSearch);
        String materialTerm = normalize(materialSearch);
        if (combinedTerm != null
            && !contains(item.getRecipeVersion().getRecipe().getRecipeCode(), combinedTerm)
            && !contains(item.getRecipeVersion().getRecipe().getRecipeName(), combinedTerm)
            && !contains(item.getRecipeVersion().getVersionDate() == null ? null : item.getRecipeVersion().getVersionDate().toString(), combinedTerm)
            && !contains(item.getRecipeVersion().getStatus() == null ? null : item.getRecipeVersion().getStatus().name(), combinedTerm)
            && !contains(item.getMaterial().getMaterialCode(), combinedTerm)
            && !contains(item.getMaterial().getMaterialName(), combinedTerm)) {
            return false;
        }
        if (recipeTerm != null && !contains(item.getRecipeVersion().getRecipe().getRecipeCode(), recipeTerm)
            && !contains(item.getRecipeVersion().getRecipe().getRecipeName(), recipeTerm)) {
            return false;
        }
        if (versionTerm != null
            && !contains(item.getRecipeVersion().getVersionDate() == null ? null : item.getRecipeVersion().getVersionDate().toString(), versionTerm)
            && !contains(item.getRecipeVersion().getStatus() == null ? null : item.getRecipeVersion().getStatus().name(), versionTerm)) {
            return false;
        }
        return materialTerm == null
            || contains(item.getMaterial().getMaterialCode(), materialTerm)
            || contains(item.getMaterial().getMaterialName(), materialTerm);
    }

    private static Criteria addCriteria(Criteria current, Criteria next) {
        return current == null ? next : current.and(next);
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String term) {
        return value != null && term != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private static RecipeVersionItemDetailResponse toResponse(RecipeVersionItemSearchDocument document) {
        return new RecipeVersionItemDetailResponse(
            document.getId() == null ? null : Long.valueOf(document.getId()),
            document.getRecipeVersionId(),
            document.getRecipeId(),
            document.getRecipeCode(),
            document.getRecipeName(),
            document.getVersionDate() == null ? null : LocalDate.parse(document.getVersionDate()),
            document.getVersionStatus(),
            document.getBaseWeightG() == null ? null : new BigDecimal(document.getBaseWeightG()),
            document.getMaterialId(),
            document.getMaterialCode(),
            document.getMaterialName(),
            document.getRatio() == null ? null : new BigDecimal(document.getRatio()),
            document.getDisplayOrder(),
            document.getCreatedAt() == null ? null : Instant.parse(document.getCreatedAt())
        );
    }

    private static RecipeVersionItemDetailResponse toResponse(RecipeVersionItem item) {
        return new RecipeVersionItemDetailResponse(
            item.getId(),
            item.getRecipeVersion().getId(),
            item.getRecipeVersion().getRecipe().getId(),
            item.getRecipeVersion().getRecipe().getRecipeCode(),
            item.getRecipeVersion().getRecipe().getRecipeName(),
            item.getRecipeVersion().getVersionDate(),
            item.getRecipeVersion().getStatus().name(),
            item.getRecipeVersion().getBaseWeightG(),
            item.getMaterial().getId(),
            item.getMaterial().getMaterialCode(),
            item.getMaterial().getMaterialName(),
            item.getRatio(),
            item.getDisplayOrder(),
            item.getCreatedAt()
        );
    }

    private static final Comparator<RecipeVersionItemDetailResponse> RESPONSE_COMPARATOR =
        Comparator.comparing(RecipeVersionItemDetailResponse::recipeCode, Comparator.nullsLast(String::compareToIgnoreCase))
            .thenComparing(RecipeVersionItemDetailResponse::versionDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(RecipeVersionItemDetailResponse::displayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(RecipeVersionItemDetailResponse::id, Comparator.nullsLast(Comparator.naturalOrder()));
}
