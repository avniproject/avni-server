package org.avni.server.dao.sync;

public final class CatchmentSyncSql {
    public static final String CATCHMENT_ADDRESS_LEVEL_IDS_SUBQUERY =
            "select al1.id from catchment cat " +
                    "inner join catchment_address_mapping cam on cat.id = cam.catchment_id " +
                    "inner join address_level al on cam.addresslevel_id = al.id " +
                    "inner join address_level al1 on al.lineage @> al1.lineage " +
                    "where cat.id = :catchmentId";

    private CatchmentSyncSql() {
    }
}
