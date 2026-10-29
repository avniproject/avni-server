package org.avni.server.web;

import jakarta.persistence.EntityManagerFactory;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.Encounter;
import org.hibernate.SessionFactory;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.Statistics;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.assertEquals;

@Sql(value = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class EncounterUpdateCountIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    @Test
    public void postingAnEncounterInsertsItWithoutRewritingIt() throws Exception {
        Object json = mapper.readValue(this.getClass().getResource("/ref/encounters/newEncounter.json"), Object.class);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            post("/encounters", json);

            EntityStatistics encounterStatistics = statistics.getEntityStatistics(Encounter.class.getName());
            assertEquals(1, encounterStatistics.getInsertCount());
            assertEquals(0, encounterStatistics.getUpdateCount());
        } finally {
            statistics.setStatisticsEnabled(statisticsWereEnabled);
        }
    }
}
