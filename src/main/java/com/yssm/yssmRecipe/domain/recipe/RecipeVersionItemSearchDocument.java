package com.yssm.yssmRecipe.domain.recipe;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "recipe-version-items")
public class RecipeVersionItemSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long recipeVersionId;

    @Field(type = FieldType.Long)
    private Long recipeId;

    @Field(type = FieldType.Text)
    private String recipeCode;

    @Field(type = FieldType.Text)
    private String recipeName;

    @Field(type = FieldType.Text)
    private String versionDate;

    @Field(type = FieldType.Text)
    private String versionStatus;

    @Field(type = FieldType.Text)
    private String baseWeightG;

    @Field(type = FieldType.Long)
    private Long materialId;

    @Field(type = FieldType.Text)
    private String materialCode;

    @Field(type = FieldType.Text)
    private String materialName;

    @Field(type = FieldType.Text)
    private String ratio;

    @Field(type = FieldType.Integer)
    private Integer displayOrder;

    @Field(type = FieldType.Text)
    private String createdAt;

    public static RecipeVersionItemSearchDocument from(RecipeVersionItem item) {
        RecipeVersion version = item.getRecipeVersion();
        RecipeVersionItemSearchDocument document = new RecipeVersionItemSearchDocument();
        document.id = String.valueOf(item.getId());
        document.recipeVersionId = version.getId();
        document.recipeId = version.getRecipe().getId();
        document.recipeCode = version.getRecipe().getRecipeCode();
        document.recipeName = version.getRecipe().getRecipeName();
        document.versionDate = version.getVersionDate() == null ? null : version.getVersionDate().toString();
        document.versionStatus = version.getStatus() == null ? null : version.getStatus().name();
        document.baseWeightG = version.getBaseWeightG() == null ? null : version.getBaseWeightG().toPlainString();
        document.materialId = item.getMaterial().getId();
        document.materialCode = item.getMaterial().getMaterialCode();
        document.materialName = item.getMaterial().getMaterialName();
        document.ratio = item.getRatio() == null ? null : item.getRatio().toPlainString();
        document.displayOrder = item.getDisplayOrder();
        document.createdAt = item.getCreatedAt() == null ? null : item.getCreatedAt().toString();
        return document;
    }

    public String getId() {
        return id;
    }

    public Long getRecipeVersionId() {
        return recipeVersionId;
    }

    public Long getRecipeId() {
        return recipeId;
    }

    public String getRecipeCode() {
        return recipeCode;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public String getVersionDate() {
        return versionDate;
    }

    public String getVersionStatus() {
        return versionStatus;
    }

    public String getBaseWeightG() {
        return baseWeightG;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public String getRatio() {
        return ratio;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
