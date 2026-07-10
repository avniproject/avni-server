-- #871 diagnostic (READ-ONLY): orgs whose lowestAddressLevelType lineages V1_399 will DROP.
-- Run as a superuser/table-owner (bypasses RLS) so all orgs are visible.
-- A lineage is dropped iff any segment is neither already a UUID nor an all-digit id (<= 18 digits, i.e.
-- in bigint range) that resolves to an address_level_type owned by the org OR one of its ANCESTOR orgs
-- (address_level_type is ref/ancestor-visible) -- mirrors the migration's resolution exactly.
-- Impacted orgs need a follow-up: re-import the (post-fix) config bundle, or re-save the location form(s).
WITH RECURSIVE org_ancestry(org_id, ancestor_id) AS (
    -- every org maps to {itself + all of its ancestors}, matching V1_399's {self + ancestors} ID resolution
    SELECT id, id FROM organisation
    UNION
    SELECT oa.org_id, o.parent_organisation_id
    FROM org_ancestry oa
    JOIN organisation o ON o.id = oa.ancestor_id
    WHERE o.parent_organisation_id IS NOT NULL
),
cfg AS (
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
               WHEN s.segment ~ '^[0-9]+$' AND length(s.segment) <= 18
                   THEN EXISTS (SELECT 1 FROM address_level_type alt
                                JOIN org_ancestry oa ON oa.ancestor_id = alt.organisation_id
                                WHERE alt.id = s.segment::bigint
                                  AND oa.org_id = cfg.organisation_id)
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
