package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.Database;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseRepository extends MetabaseConnector {
    public DatabaseRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public Database save(Database database) {
        String url = metabaseApiUrl + "/database";
        Database response = postForObject(url, database, Database.class);
        database.setId(response.getId());
        return database;
    }
}
