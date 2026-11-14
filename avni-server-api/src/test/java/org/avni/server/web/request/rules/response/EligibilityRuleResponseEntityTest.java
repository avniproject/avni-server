package org.avni.server.web.request.rules.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.avni.server.util.ObjectMapperSingleton;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EligibilityRuleResponseEntityTest {
    @Test
    public void aRuleResultThatIsNotTrueOrFalseFailsOnlyThatType() throws JsonProcessingException {
        Map<String, EligibilityRuleEntity> results = read("[" +
                "{\"typeUUID\": \"yes\", \"isEligible\": true}," +
                "{\"typeUUID\": \"no\", \"isEligible\": false}," +
                "{\"typeUUID\": \"text-no\", \"isEligible\": \"no\"}," +
                "{\"typeUUID\": \"object\", \"isEligible\": {\"eligible\": true}}," +
                "{\"typeUUID\": \"async\", \"isEligible\": {}}," +
                "{\"typeUUID\": \"list\", \"isEligible\": [true]}" +
                "]");

        assertEligible(results.get("yes"));
        assertNotEligible(results.get("no"));
        assertFailed(results.get("text-no"), "Eligibility rule returned \"no\"; it must return true or false");
        assertFailed(results.get("object"), "Eligibility rule returned {eligible=true}; it must return true or false");
        assertFailed(results.get("async"), "Eligibility rule returned {}; it must return true or false");
        assertFailed(results.get("list"), "Eligibility rule returned [true]; it must return true or false");
    }

    @Test
    public void resultsTheServerAlreadyReadAsTrueOrFalseAreReadTheSameWay() throws JsonProcessingException {
        Map<String, EligibilityRuleEntity> results = read("[" +
                "{\"typeUUID\": \"missing\"}," +
                "{\"typeUUID\": \"null\", \"isEligible\": null}," +
                "{\"typeUUID\": \"zero\", \"isEligible\": 0}," +
                "{\"typeUUID\": \"one\", \"isEligible\": 1}," +
                "{\"typeUUID\": \"empty-text\", \"isEligible\": \"\"}," +
                "{\"typeUUID\": \"text-false\", \"isEligible\": \"false\"}," +
                "{\"typeUUID\": \"text-true\", \"isEligible\": \"TRUE\"}" +
                "]");

        assertNotEligible(results.get("missing"));
        assertNotEligible(results.get("null"));
        assertNotEligible(results.get("zero"));
        assertEligible(results.get("one"));
        assertNotEligible(results.get("empty-text"));
        assertNotEligible(results.get("text-false"));
        assertEligible(results.get("text-true"));
    }

    @Test
    public void theErrorOfARuleThatThrewIsKept() throws JsonProcessingException {
        EligibilityRuleEntity thrown = read("[{\"typeUUID\": \"throwing\", \"isEligible\": true, \"error\": {\"message\": \"rule threw\", \"stack\": \"at rule\"}}]").get("throwing");

        assertTrue(thrown.hasFailed());
        assertEquals("rule threw", thrown.getError().getMessage());
        assertEquals("at rule", thrown.getError().getStack());
    }

    private static Map<String, EligibilityRuleEntity> read(String entities) throws JsonProcessingException {
        EligibilityRuleResponseEntity response = ObjectMapperSingleton.getObjectMapper()
                .readValue("{\"status\": \"success\", \"eligibilityRuleEntities\": " + entities + "}", EligibilityRuleResponseEntity.class);
        List<EligibilityRuleEntity> eligibilityRuleEntities = response.getEligibilityRuleEntities();
        return eligibilityRuleEntities.stream().collect(Collectors.toMap(EligibilityRuleEntity::getTypeUUID, Function.identity()));
    }

    private static void assertEligible(EligibilityRuleEntity result) {
        assertTrue(result.isEligible());
        assertFalse(result.hasFailed());
    }

    private static void assertNotEligible(EligibilityRuleEntity result) {
        assertFalse(result.isEligible());
        assertFalse(result.hasFailed());
    }

    private static void assertFailed(EligibilityRuleEntity result, String message) {
        assertTrue(result.hasFailed());
        assertEquals(message, result.getError().getMessage());
        assertEquals(message, result.getError().getStack());
    }
}
