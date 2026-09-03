CREATE TABLE material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(255) NOT NULL,
    base_unit VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_material_code UNIQUE (material_code)
);

CREATE TABLE recipe (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipe_code VARCHAR(64) NOT NULL,
    recipe_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_recipe_code UNIQUE (recipe_code)
);

CREATE TABLE recipe_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipe_id BIGINT NOT NULL,
    version_date DATE NOT NULL,
    base_weight_g DECIMAL(18, 6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(128),
    CONSTRAINT fk_recipe_version_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id),
    CONSTRAINT uk_recipe_version UNIQUE (recipe_id, version_date)
);

CREATE INDEX idx_recipe_version_recipe_status_date
    ON recipe_version (recipe_id, status, version_date DESC, id DESC);

CREATE TABLE recipe_version_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipe_version_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    ratio DECIMAL(18, 6) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_recipe_version_item_version FOREIGN KEY (recipe_version_id) REFERENCES recipe_version (id),
    CONSTRAINT fk_recipe_version_item_material FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT uk_recipe_version_material UNIQUE (recipe_version_id, material_id)
);

CREATE INDEX idx_recipe_version_item_version_order
    ON recipe_version_item (recipe_version_id, display_order, id);
