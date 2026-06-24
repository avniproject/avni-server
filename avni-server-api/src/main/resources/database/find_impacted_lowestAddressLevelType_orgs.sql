-- #871 diagnostic (READ-ONLY): orgs whose lowestAddressLevelType lineages V1_399 will DROP.
-- Run as a superuser/table-owner (bypasses RLS) so all orgs are visible.
-- A lineage is dropped iff any segment is neither already a UUID nor an all-digit id that
-- resolves to an address_level_type in the SAME org (mirrors the migration exactly).
-- Impacted orgs need a follow-up: re-import the (post-fix) config bundle, or re-save the location form(s).
WITH cfg AS (
    SELECT oc.id              AS org_config_id,
           oc.organisation_id,
           o.name             AS org_name,
           lineage.value      AS lineage
    FROM organisation_config oc
    JOIN organisation o ON o.id = oc.organisation_id
    CROSS JOIN LATERAL jsonb_array_elements_text(oc.settings -> 'lowestAddressLevelType') AS lineage(value)
    WHERE oc.settings ? 'lowestAddressLevelType'
      AND jsonb_typeof(oc.settings -> 'lowestAddressLevelType') = 'array'
),
seg AS (
    SELECT cfg.organisation_id,
           cfg.org_name,
           cfg.lineage,
           CASE
               WHEN s.segment ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
                   THEN true
               WHEN s.segment ~ '^[0-9]+$'
                   THEN EXISTS (SELECT 1 FROM address_level_type alt
                                WHERE alt.id = s.segment::bigint
                                  AND alt.organisation_id = cfg.organisation_id)
               ELSE false
           END AS segment_resolvable
    FROM cfg
    CROSS JOIN LATERAL unnest(string_to_array(cfg.lineage, '.')) AS s(segment)
),
lineage_status AS (
    SELECT organisation_id, org_name, lineage,
           bool_and(segment_resolvable) AS lineage_ok
    FROM seg
    GROUP BY organisation_id, org_name, lineage
)
SELECT organisation_id,
       org_name,
       count(*) FILTER (WHERE NOT lineage_ok)                      AS lineages_dropped,
       count(*)                                                    AS lineages_total,
       count(*) FILTER (WHERE NOT lineage_ok) = count(*)           AS fully_wiped,
       array_agg(lineage) FILTER (WHERE NOT lineage_ok)            AS dropped_lineages
FROM lineage_status
GROUP BY organisation_id, org_name
HAVING count(*) FILTER (WHERE NOT lineage_ok) > 0
ORDER BY fully_wiped DESC, lineages_dropped DESC, organisation_id;
