-- Seed roles, permissions, system units, and demo bootstrap data

INSERT INTO roles (id, code, name, description) VALUES
 (gen_random_uuid(), 'SUPER_ADMIN', 'Super Admin', 'Platform administrator'),
 (gen_random_uuid(), 'ORGANIZATION_ADMIN', 'Organization Admin', 'Full organization access'),
 (gen_random_uuid(), 'ACCOUNTANT', 'Accountant', 'Accounting and payments'),
 (gen_random_uuid(), 'SALES_MANAGER', 'Sales Manager', 'Sales documents'),
 (gen_random_uuid(), 'PURCHASE_MANAGER', 'Purchase Manager', 'Purchase documents'),
 (gen_random_uuid(), 'INVENTORY_MANAGER', 'Inventory Manager', 'Inventory operations'),
 (gen_random_uuid(), 'VIEWER', 'Viewer', 'Read-only access');

INSERT INTO permissions (id, code, name, module) VALUES
 (gen_random_uuid(), 'ORG_READ', 'View organization', 'ORGANIZATION'),
 (gen_random_uuid(), 'ORG_WRITE', 'Manage organization', 'ORGANIZATION'),
 (gen_random_uuid(), 'USER_READ', 'View users', 'USER'),
 (gen_random_uuid(), 'USER_WRITE', 'Manage users', 'USER'),
 (gen_random_uuid(), 'CUSTOMER_READ', 'View customers', 'CUSTOMER'),
 (gen_random_uuid(), 'CUSTOMER_WRITE', 'Manage customers', 'CUSTOMER'),
 (gen_random_uuid(), 'SUPPLIER_READ', 'View suppliers', 'SUPPLIER'),
 (gen_random_uuid(), 'SUPPLIER_WRITE', 'Manage suppliers', 'SUPPLIER'),
 (gen_random_uuid(), 'PRODUCT_READ', 'View products', 'PRODUCT'),
 (gen_random_uuid(), 'PRODUCT_WRITE', 'Manage products', 'PRODUCT'),
 (gen_random_uuid(), 'INVENTORY_READ', 'View inventory', 'INVENTORY'),
 (gen_random_uuid(), 'INVENTORY_WRITE', 'Manage inventory', 'INVENTORY'),
 (gen_random_uuid(), 'SALES_READ', 'View sales', 'SALES'),
 (gen_random_uuid(), 'SALES_WRITE', 'Manage sales', 'SALES'),
 (gen_random_uuid(), 'PURCHASE_READ', 'View purchases', 'PURCHASE'),
 (gen_random_uuid(), 'PURCHASE_WRITE', 'Manage purchases', 'PURCHASE'),
 (gen_random_uuid(), 'PAYMENT_READ', 'View payments', 'PAYMENT'),
 (gen_random_uuid(), 'PAYMENT_WRITE', 'Manage payments', 'PAYMENT'),
 (gen_random_uuid(), 'REPORT_READ', 'View reports', 'REPORT'),
 (gen_random_uuid(), 'TEMPLATE_READ', 'View templates', 'TEMPLATE'),
 (gen_random_uuid(), 'TEMPLATE_WRITE', 'Manage templates', 'TEMPLATE'),
 (gen_random_uuid(), 'AUDIT_READ', 'View audit logs', 'AUDIT');

-- Map ORGANIZATION_ADMIN to all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ORGANIZATION_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'ORG_READ','CUSTOMER_READ','CUSTOMER_WRITE','SUPPLIER_READ','PRODUCT_READ','PRODUCT_WRITE',
  'INVENTORY_READ','SALES_READ','SALES_WRITE','PAYMENT_READ','PAYMENT_WRITE','REPORT_READ','TEMPLATE_READ'
) WHERE r.code = 'ACCOUNTANT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'CUSTOMER_READ','CUSTOMER_WRITE','PRODUCT_READ','INVENTORY_READ','SALES_READ','SALES_WRITE','PAYMENT_READ','REPORT_READ'
) WHERE r.code = 'SALES_MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'SUPPLIER_READ','SUPPLIER_WRITE','PRODUCT_READ','INVENTORY_READ','PURCHASE_READ','PURCHASE_WRITE','PAYMENT_READ','REPORT_READ'
) WHERE r.code = 'PURCHASE_MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'PRODUCT_READ','PRODUCT_WRITE','INVENTORY_READ','INVENTORY_WRITE','REPORT_READ'
) WHERE r.code = 'INVENTORY_MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code LIKE '%_READ' WHERE r.code = 'VIEWER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

INSERT INTO units (id, organization_id, code, name, system_unit) VALUES
 (gen_random_uuid(), NULL, 'PCS', 'Pieces', TRUE),
 (gen_random_uuid(), NULL, 'KG', 'Kilogram', TRUE),
 (gen_random_uuid(), NULL, 'GRAM', 'Gram', TRUE),
 (gen_random_uuid(), NULL, 'LITRE', 'Litre', TRUE),
 (gen_random_uuid(), NULL, 'ML', 'Millilitre', TRUE),
 (gen_random_uuid(), NULL, 'BOX', 'Box', TRUE),
 (gen_random_uuid(), NULL, 'PACK', 'Pack', TRUE),
 (gen_random_uuid(), NULL, 'METER', 'Meter', TRUE),
 (gen_random_uuid(), NULL, 'FEET', 'Feet', TRUE),
 (gen_random_uuid(), NULL, 'DOZEN', 'Dozen', TRUE);
