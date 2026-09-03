CREATE TABLE product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    safety_stock INT NOT NULL,
    max_stock INT NOT NULL,
    erp_unit VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_product_code UNIQUE (product_code)
);

CREATE TABLE product_packaging (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    erp_unit VARCHAR(32) NOT NULL,
    gram_weight_per_erp_unit DECIMAL(18, 6) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_product_packaging_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE product_recipe_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    recipe_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_product_recipe_mapping_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_product_recipe_mapping_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id)
);

CREATE TABLE production_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    current_stock INT NOT NULL,
    safety_stock INT NOT NULL,
    target_stock INT NOT NULL,
    suggested_quantity INT NOT NULL,
    planned_quantity INT NOT NULL,
    calculation_mode VARCHAR(16) NOT NULL,
    gram_weight_per_erp_unit DECIMAL(18, 6) NOT NULL,
    calculation_weight_g DECIMAL(18, 6) NOT NULL,
    recipe_version_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_production_plan_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_production_plan_recipe_version FOREIGN KEY (recipe_version_id) REFERENCES recipe_version (id)
);

CREATE TABLE material_requirement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    production_plan_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    recipe_version_id BIGINT NOT NULL,
    ratio DECIMAL(18, 6) NOT NULL,
    percentage DECIMAL(18, 6) NOT NULL,
    required_weight_g DECIMAL(18, 6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_material_requirement_plan FOREIGN KEY (production_plan_id) REFERENCES production_plan (id),
    CONSTRAINT fk_material_requirement_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT fk_material_requirement_recipe_version FOREIGN KEY (recipe_version_id) REFERENCES recipe_version (id)
);
