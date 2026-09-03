CREATE TABLE product_stock_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    stock_quantity INT NOT NULL,
    source_file_name VARCHAR(255),
    imported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_product_stock_snapshot_product FOREIGN KEY (product_id) REFERENCES product (id),
    KEY idx_product_stock_snapshot_product_imported_at (product_id, imported_at, id)
);

CREATE TABLE material_inventory_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    stock_quantity DECIMAL(18, 6) NOT NULL,
    source_file_name VARCHAR(255),
    imported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_material_inventory_snapshot_material FOREIGN KEY (material_id) REFERENCES material (id),
    KEY idx_material_inventory_snapshot_material_imported_at (material_id, imported_at, id)
);
