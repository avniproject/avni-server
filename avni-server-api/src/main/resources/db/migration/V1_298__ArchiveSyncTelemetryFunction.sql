DROP FUNCTION IF EXISTS archive_sync_telemetry(olderthan date);
CREATE FUNCTION archive_sync_telemetry(olderthan date) RETURNS bigint AS $$
BEGIN
    CREATE TABLE IF NOT EXISTS public.sync_telemetry_history (LIKE public.sync_telemetry INCLUDING ALL);
    PERFORM enable_rls_on_tx_table('sync_telemetry_history');
    INSERT INTO public.sync_telemetry_history SELECT * from public.sync_telemetry WHERE sync_start_time < olderthan;
    DELETE FROM public.sync_telemetry WHERE sync_start_time < olderthan;
    RETURN 1;
END;
$$ LANGUAGE plpgsql;