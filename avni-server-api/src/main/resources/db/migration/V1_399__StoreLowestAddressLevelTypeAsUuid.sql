-- #871 | Make organisation_config.settings.lowestAddressLevelType portable across orgs.
--
-- The setting stores dot-joined address_level_type lineages. Historically these were
-- database serial IDs (e.g. "4291.4292.4293.4294"), which are per-environment and so
-- break config bundle transfer (dev -> uat -> prod). This migration rewrites each
-- lineage to dot-joined UUIDs, which are stable across orgs/environments.
--
-- Properties:
--   * Idempotent  - segments already in UUID form are kept as-is, so re-running is a no-op.
--   * Org-scoped  - an ID segment resolves only against the same org's address_level_type;
--                   a foreign/non-existent ID (cross-env garbage, e.g. CSJ's "dummy" chain
--                   or a UAT->prod overwrite) makes the whole lineage unresolvable.
--   * Pruning     - any lineage with an unresolvable segment is dropped (and reported via
--                   RAISE NOTICE), rather than persisting a value that can never resolve.
--
-- Deploy together with the code change that reads/writes UUID lineages. Do NOT roll back to
-- the old code after this runs: the legacy Long-based decoder throws on UUID segments.

DO
$$
    DECLARE
        cfg             RECORD;
        lineage         TEXT;
        segment         TEXT;
        converted       TEXT;
        new_lineages    JSONB;
        lineage_ok      BOOLEAN;
        seg_uuid        TEXT;
        visible_org_ids BIGINT[];
        uuid_pattern    TEXT := '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';
    BEGIN
        FOR cfg IN
            SELECT id, organisation_id, settings
            FROM organisation_config
            WHERE settings ? 'lowestAddressLevelType'
              AND jsonb_typeof(settings -> 'lowestAddressLevelType') = 'array'
            LOOP
              -- Per-org savepoint: isolate any unexpected failure to the one org (logged + skipped) instead of
              -- rolling back the conversion for every org and blocking the deploy.
              BEGIN
                -- An id segment must resolve against the same visibility the value was written under, not own-org
                -- only: address_level_type is a ref table (org-group + ancestor visible), so a child/group org's
                -- lineage can legitimately reference an ALT owned by an ancestor org. Resolving against
                -- {self + ancestors} keeps those (matching the runtime findAllByIdIn under RLS) while still
                -- rejecting cross-environment ids (a different env's org is never in this org's ancestry).
                WITH RECURSIVE ancestry(org_id) AS (
                    SELECT cfg.organisation_id
                    UNION
                    SELECT o.parent_organisation_id
                    FROM public.organisation o
                             JOIN ancestry a ON o.id = a.org_id
                    WHERE o.parent_organisation_id IS NOT NULL
                )
                SELECT array_agg(org_id) INTO visible_org_ids FROM ancestry;

                new_lineages := '[]'::jsonb;

                FOR lineage IN
                    SELECT jsonb_array_elements_text(cfg.settings -> 'lowestAddressLevelType')
                    LOOP
                        converted := '';
                        lineage_ok := TRUE;

                        FOREACH segment IN ARRAY string_to_array(lineage, '.')
                            LOOP
                                IF segment ~ uuid_pattern THEN
                                    -- already a UUID (idempotent re-run or mixed value): keep
                                    seg_uuid := segment;
                                ELSIF segment ~ '^[0-9]+$' AND length(segment) <= 18 THEN
                                    -- length <= 18 keeps the value inside bigint range (max 18-digit value
                                    -- < 9.2e18); a longer all-digit segment is cross-env junk, not a real id, so
                                    -- it falls through to NULL (unresolvable) rather than raising on the cast and
                                    -- aborting the whole migration for every org.
                                    SELECT alt.uuid
                                    INTO seg_uuid
                                    FROM address_level_type alt
                                    WHERE alt.id = segment::BIGINT
                                      AND alt.organisation_id = ANY(visible_org_ids);
                                ELSE
                                    seg_uuid := NULL;
                                END IF;

                                IF seg_uuid IS NULL THEN
                                    lineage_ok := FALSE;
                                    EXIT;
                                END IF;

                                converted := CASE
                                                 WHEN converted = '' THEN seg_uuid
                                                 ELSE converted || '.' || seg_uuid
                                    END;
                            END LOOP;

                        IF lineage_ok THEN
                            new_lineages := new_lineages || to_jsonb(converted);
                        ELSE
                            RAISE NOTICE 'organisation_config % (org %): dropping unresolvable lowestAddressLevelType lineage "%"',
                                cfg.id, cfg.organisation_id, lineage;
                        END IF;
                    END LOOP;

                UPDATE organisation_config
                SET settings = jsonb_set(settings, '{lowestAddressLevelType}', new_lineages, false)
                WHERE id = cfg.id;
              EXCEPTION WHEN OTHERS THEN
                RAISE NOTICE 'V1_399: skipped organisation_config % (org %) due to error: % (its lowestAddressLevelType is left unchanged for manual follow-up)',
                    cfg.id, cfg.organisation_id, SQLERRM;
              END;
            END LOOP;
    END
$$;
