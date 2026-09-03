package com.yssm.yssmRecipe.dto.recipe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecipeVersionUpsertRequest(
    @NotNull LocalDate versionDate,
    @NotNull BigDecimal baseWeightG,
    @NotBlank String status,
    String createdBy,
    @Valid List<RecipeVersionItemRequest> items
) {
}
