CREATE OR REPLACE FUNCTION no_op() RETURNS trigger AS $$
BEGIN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER delete_on_virtual_catchment_address_mapping
    INSTEAD OF DELETE
    ON virtual_catchment_address_mapping_table
    FOR EACH ROW
EXECUTE FUNCTION no_op();

SELECT grant_all_on_table(a.rolname, 'virtual_catchment_address_mapping_table') FROM pg_roles a WHERE pg_has_role('openchs', a.oid, 'member');

