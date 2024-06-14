package org.avni.server.dao.metabase;

import org.avni.server.domain.metabase.Database;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseRepository extends MetabaseRepository{
    public DatabaseRepository(RestTemplateBuilder restTemplateBuilder) {
        super(restTemplateBuilder);
    }

    public Database save(Database database) {
        String url = metabaseApiUrl + "/database";
        HttpEntity<Database> entity = createHttpEntity(database);
        Database response = restTemplate.postForObject(url, entity, Database.class);

        return new Database(response.getId(), database.getName(), database.getEngine(), database.getDetails());
    }
}
