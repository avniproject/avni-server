package org.avni.server.service;

import jakarta.transaction.Transactional;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
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

    @Test
    public void findByAddressMap() {
        testDataSetupService.setupOrganisation();
        AddressLevelType grandParent = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withUuid("GP").name("GP").level(3d).build());
        AddressLevelType parent = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withUuid("Parent").name("Parent").parent(grandParent).level(2d).build());
        AddressLevelType child = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withUuid("Child").name("Child").parent(parent).level(1d).build());

        AddressLevel gp1AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("gp1").title("gp1").type(grandParent).build());
        AddressLevel parent1AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("parent1").title("parent1").type(parent).parent(gp1AddressLevel).build());
        AddressLevel child1AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("child1").title("child1").type(child).parent(parent1AddressLevel).build());
        AddressLevel gp2AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("gp2").title("gp2").type(grandParent).build());
        AddressLevel parent2AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("parent2").title("parent2").type(parent).parent(gp2AddressLevel).build());
        AddressLevel child2AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("child2").title("child2").type(child).parent(parent2AddressLevel).build());
        AddressLevel gp3AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("gp3").title("gp3").type(grandParent).build());
        AddressLevel parent3AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("parent3").title("parent2").type(parent).parent(gp3AddressLevel).build());
        AddressLevel child3AddressLevel = testLocationService.save(new AddressLevelBuilder().withUuid("child3").title("child1").type(child).parent(parent3AddressLevel).build());

        Map<String, String> addressLevelMap = new HashMap<>();

        //valid hierarchy
        addressLevelMap.put(parent.getName(), parent1AddressLevel.getTitle());
        addressLevelMap.put(child.getName(), child1AddressLevel.getTitle());
        addressLevelMap.put(grandParent.getName(), gp1AddressLevel.getTitle());
        assertEquals(child1AddressLevel, addressLevelService.findByAddressMap(addressLevelMap).get());

        //valid locations in valid hierarchy but not related
        addressLevelMap.put(grandParent.getName(), gp2AddressLevel.getTitle());
        assertEquals(Optional.empty(), addressLevelService.findByAddressMap(addressLevelMap));
        addressLevelMap.put(child.getName(), child2AddressLevel.getTitle());
        assertEquals(Optional.empty(), addressLevelService.findByAddressMap(addressLevelMap));

        addressLevelMap.put(parent.getName(), parent2AddressLevel.getTitle());
        assertEquals(child2AddressLevel, addressLevelService.findByAddressMap(addressLevelMap).get());

        //case insensitive
        addressLevelMap.remove(parent.getName());
        addressLevelMap.put(parent.getName().toUpperCase(), parent2AddressLevel.getTitle().toUpperCase());
        assertEquals(child2AddressLevel, addressLevelService.findByAddressMap(addressLevelMap).get());

        //partial hierarchy
        addressLevelMap.remove(grandParent.getName());
        assertEquals(Optional.empty(), addressLevelService.findByAddressMap(addressLevelMap));

        //valid locations in valid hierarchy and related, but with duplicate title
        addressLevelMap.put(grandParent.getName(), gp3AddressLevel.getTitle());
        assertEquals(Optional.empty(), addressLevelService.findByAddressMap(addressLevelMap));
        addressLevelMap.remove(parent.getName().toUpperCase());
        addressLevelMap.put(parent.getName(), parent3AddressLevel.getTitle());
        assertEquals(Optional.empty(), addressLevelService.findByAddressMap(addressLevelMap));
        addressLevelMap.put(child.getName(), child3AddressLevel.getTitle());
        assertEquals(child3AddressLevel, addressLevelService.findByAddressMap(addressLevelMap).get());
    }
}
