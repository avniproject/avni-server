CREATE INDEX IF NOT EXISTS address_level_lineage_text_idx
  ON address_level (((lineage::text) COLLATE "C") text_pattern_ops);

CREATE INDEX IF NOT EXISTS address_level_type_id_idx
  ON address_level (type_id);
