DROP FUNCTION IF EXISTS archive_sync_telemetry(olderthan date);
CREATE FUNCTION archive_sync_telemetry(olderthan date) RETURNS bigint AS $$
DECLARE
    archived_row_count bigint;
BEGIN
    CREATE TABLE IF NOT EXISTS public.sync_telemetry_history (LIKE public.sync_telemetry INCLUDING ALL);
    PERFORM enable_rls_on_tx_table('sync_telemetry_history');
    INSERT INTO public.sync_telemetry_history SELECT * from public.sync_telemetry WHERE sync_start_time < olderthan;
    DELETE FROM public.sync_telemetry WHERE sync_start_time < olderthan;
    GET DIAGNOSTICS archived_row_count = ROW_COUNT;
    RETURN archived_row_count;
END;
$$ LANGUAGE plpgsql;