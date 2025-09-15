UPDATE groups
SET name = name || ' (voided~' || id || ')'
WHERE is_voided = true 
  AND name NOT LIKE '%(voided~%)';