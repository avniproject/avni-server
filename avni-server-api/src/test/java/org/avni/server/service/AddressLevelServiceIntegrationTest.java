package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestLocationService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AddressLevelServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private AddressLevelService addressLevelService;

    @Test
    public void getTitleLineages() {
        testDataSetupService.setupOrganisation();
        AddressLevelType city = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withUuid("city").name("city").level(1d).build());
        AddressLevelType state = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withUuid("state").name("state").level(2d).child(city).build());

        AddressLevel bangalore = testLocationService.save(new AddressLevelBuilder().withUuid("bangalore").title("bangalore").type(city).build());
        AddressLevel kochi = testLocationService.save(new AddressLevelBuilder().withUuid("kochi").title("kochi").type(city).build());

        testLocationService.save(new AddressLevelBuilder().withUuid("karnataka").title("karnataka").type(state).child(bangalore).build());
        testLocationService.save(new AddressLevelBuilder().withUuid("kerala").title("kerala").type(state).child(kochi).build());

        Map<Long, String> titleLineages = addressLevelService.getTitleLineages(Arrays.asList(bangalore.getId(), kochi.getId()));
        assertEquals("karnataka, bangalore", titleLineages.get(bangalore.getId()));
        assertEquals("kerala, kochi", titleLineages.get(kochi.getId()));
    }
}
