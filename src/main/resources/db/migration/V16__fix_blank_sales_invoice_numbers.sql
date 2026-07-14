-- Blank invoice_number collides on uq_sales_invoices_number (org can only have one "").
-- Uniquify existing blanks so new drafts can allocate real numbers.
UPDATE sales_invoices
SET invoice_number = 'DRAFT-' || id::text
WHERE invoice_number IS NULL OR btrim(invoice_number) = '';
