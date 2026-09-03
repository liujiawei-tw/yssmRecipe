CREATE TABLE purchase_suggestion_analysis (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    generated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    total_required_weight_g DECIMAL(18, 6) NOT NULL,
    total_stock_weight_g DECIMAL(18, 6) NOT NULL,
    total_shortage_weight_g DECIMAL(18, 6) NOT NULL
);

CREATE TABLE purchase_suggestion_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    analysis_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(255) NOT NULL,
    required_weight_g DECIMAL(18, 6) NOT NULL,
    stock_weight_g DECIMAL(18, 6) NOT NULL,
    shortage_weight_g DECIMAL(18, 6) NOT NULL,
    purchase_suggestion_weight_g DECIMAL(18, 6) NOT NULL,
    inventory_available BOOLEAN NOT NULL,
    inventory_imported_at TIMESTAMP(6),
    inventory_source_file_name VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_purchase_suggestion_item_analysis FOREIGN KEY (analysis_id) REFERENCES purchase_suggestion_analysis (id),
    CONSTRAINT fk_purchase_suggestion_item_material FOREIGN KEY (material_id) REFERENCES material (id)
);

CREATE TABLE purchase_suggestion_item_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    production_plan_id BIGINT NOT NULL,
    material_requirement_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    recipe_id BIGINT NOT NULL,
    recipe_code VARCHAR(64) NOT NULL,
    recipe_name VARCHAR(255) NOT NULL,
    recipe_version_id BIGINT NOT NULL,
    recipe_version_date DATE NOT NULL,
    planned_quantity INT NOT NULL,
    calculation_mode VARCHAR(16) NOT NULL,
    calculation_weight_g DECIMAL(18, 6) NOT NULL,
    required_weight_g DECIMAL(18, 6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_purchase_suggestion_source_item FOREIGN KEY (item_id) REFERENCES purchase_suggestion_item (id),
    CONSTRAINT fk_purchase_suggestion_source_plan FOREIGN KEY (production_plan_id) REFERENCES production_plan (id),
    CONSTRAINT fk_purchase_suggestion_source_requirement FOREIGN KEY (material_requirement_id) REFERENCES material_requirement (id)
);
