-- Clarify seed workflow copy: ACTIVE workflows now gate sales convert/confirm.

UPDATE ai_workflow_drafts
SET description = 'Requires admin approval (then accountant review) for quotation, sales order, and invoice actions over ₹50,000. When ACTIVE, matching convert/confirm is blocked until approved under AI → Workflows.',
    updated_at = NOW()
WHERE name = 'Seed: Sales document approval'
  AND (
    description ILIKE '%stores config only%'
    OR description ILIKE '%advisory workflow%'
    OR description ILIKE '%does not auto-approve%'
  );
