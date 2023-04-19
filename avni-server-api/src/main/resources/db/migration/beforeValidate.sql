DO $$
    BEGIN
        IF EXISTS
            ( SELECT 1
              FROM   information_schema.tables
              WHERE  table_schema = 'public'
                AND    table_name = 'schema_version'
            )
        THEN
            update schema_version
            set checksum = 1576932710
            where version = '1.282' and checksum=-1686581224;
        END IF ;
    END
$$ ;