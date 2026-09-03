package com.yssm.yssmRecipe.service.product;

import com.yssm.yssmRecipe.domain.recipe.Product;
import com.yssm.yssmRecipe.domain.recipe.ProductPackaging;
import com.yssm.yssmRecipe.domain.recipe.ProductRecipeMapping;
import com.yssm.yssmRecipe.domain.recipe.Recipe;
import com.yssm.yssmRecipe.dto.product.ProductResponse;
import com.yssm.yssmRecipe.dto.product.ProductUpsertRequest;
import com.yssm.yssmRecipe.exception.NotFoundException;
import com.yssm.yssmRecipe.repository.MaterialRequirementRepository;
import com.yssm.yssmRecipe.repository.ProductPackagingRepository;
import com.yssm.yssmRecipe.repository.ProductRecipeMappingRepository;
import com.yssm.yssmRecipe.repository.ProductRepository;
import com.yssm.yssmRecipe.repository.ProductStockSnapshotRepository;
import com.yssm.yssmRecipe.repository.ProductionPlanRepository;
import com.yssm.yssmRecipe.repository.PurchaseSuggestionItemSourceRepository;
import com.yssm.yssmRecipe.repository.RecipeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductPackagingRepository productPackagingRepository;
    private final ProductRecipeMappingRepository productRecipeMappingRepository;
    private final ProductStockSnapshotRepository productStockSnapshotRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final MaterialRequirementRepository materialRequirementRepository;
    private final PurchaseSuggestionItemSourceRepository purchaseSuggestionItemSourceRepository;
    private final RecipeRepository recipeRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return productRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ProductResponse create(ProductUpsertRequest request) {
        productRepository.findByProductCode(request.productCode()).ifPresent(existing -> {
            throw new IllegalArgumentException("productCode 已存在");
        });
        Product product = new Product(request.productCode(), request.productName(), request.safetyStock(), request.maxStock(), request.erpUnit());
        product.setActive(request.active() == null || request.active());
        Product saved = productRepository.save(product);
        savePackaging(saved, request);
        saveRecipeMapping(saved, request.recipeId());
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpsertRequest request) {
        Product product = find(id);
        if (!product.getProductCode().equals(request.productCode())) {
            productRepository.findByProductCode(request.productCode()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("productCode 已存在");
                }
            });
        }
        product.setProductCode(request.productCode());
        product.setProductName(request.productName());
        product.setSafetyStock(request.safetyStock());
        product.setMaxStock(request.maxStock());
        product.setErpUnit(request.erpUnit());
        product.setActive(request.active() == null || request.active());
        Product saved = productRepository.save(product);
        replacePackaging(saved, request);
        replaceRecipeMapping(saved, request.recipeId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Product product = find(id);
        try {
            purchaseSuggestionItemSourceRepository.deleteByProductId(id);
            materialRequirementRepository.deleteByProductionPlanProductId(id);
            productionPlanRepository.deleteByProductId(id);
            productStockSnapshotRepository.deleteByProductId(id);
            productRecipeMappingRepository.deleteByProductId(id);
            productPackagingRepository.deleteByProductId(id);
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("商品仍被其他資料引用，無法刪除", ex);
        }
    }

    public Product find(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("找不到商品: " + id));
    }

    public ProductPackaging findActivePackaging(Long productId) {
        return productPackagingRepository.findFirstByProductIdAndActiveTrueOrderByIdDesc(productId)
            .orElseThrow(() -> new IllegalArgumentException("商品尚未設定包裝換算"));
    }

    public ProductRecipeMapping findPrimaryRecipeMapping(Long productId) {
        return productRecipeMappingRepository.findFirstByProductIdAndActiveTrueAndPrimaryMappingTrueOrderByDisplayOrderAscIdAsc(productId)
            .orElseThrow(() -> new IllegalArgumentException("商品尚未設定配方映射"));
    }

    private void savePackaging(Product product, ProductUpsertRequest request) {
        ProductPackaging packaging = new ProductPackaging(product, request.packagingErpUnit(), request.gramWeightPerErpUnit(), request.packagingDescription());
        packaging.setActive(true);
        productPackagingRepository.save(packaging);
    }

    private void replacePackaging(Product product, ProductUpsertRequest request) {
        productPackagingRepository.findFirstByProductIdAndActiveTrueOrderByIdDesc(product.getId()).ifPresent(existing -> {
            existing.setActive(false);
            productPackagingRepository.save(existing);
        });
        savePackaging(product, request);
    }

    private void saveRecipeMapping(Product product, Long recipeId) {
        if (recipeId == null) {
            return;
        }
        Recipe recipe = recipeRepository.findById(recipeId)
            .orElseThrow(() -> new NotFoundException("找不到配方: " + recipeId));
        productRecipeMappingRepository.save(new ProductRecipeMapping(product, recipe, true, true, 1));
    }

    private void replaceRecipeMapping(Product product, Long recipeId) {
        productRecipeMappingRepository.findByProductIdOrderByDisplayOrderAscIdAsc(product.getId()).forEach(existing -> {
            existing.setActive(false);
            existing.setPrimaryMapping(false);
            productRecipeMappingRepository.save(existing);
        });
        if (recipeId != null) {
            saveRecipeMapping(product, recipeId);
        }
    }

    private ProductResponse toResponse(Product product) {
        ProductPackaging packaging = productPackagingRepository.findFirstByProductIdAndActiveTrueOrderByIdDesc(product.getId()).orElse(null);
        ProductRecipeMapping mapping = productRecipeMappingRepository.findFirstByProductIdAndActiveTrueAndPrimaryMappingTrueOrderByDisplayOrderAscIdAsc(product.getId()).orElse(null);
        Recipe recipe = mapping == null ? null : mapping.getRecipe();
        return new ProductResponse(
            product.getId(),
            product.getProductCode(),
            product.getProductName(),
            product.getSafetyStock(),
            product.getMaxStock(),
            product.getErpUnit(),
            product.isActive(),
            recipe == null ? null : recipe.getId(),
            recipe == null ? null : recipe.getRecipeCode(),
            recipe == null ? null : recipe.getRecipeName(),
            packaging == null ? null : packaging.getErpUnit(),
            packaging == null ? null : packaging.getGramWeightPerErpUnit(),
            packaging == null ? null : packaging.getDescription()
        );
    }
}
