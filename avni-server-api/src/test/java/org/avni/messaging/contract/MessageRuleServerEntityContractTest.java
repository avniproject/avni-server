package org.avni.messaging.contract;

import org.avni.messaging.contract.web.MessageRequestResponse;
import org.avni.messaging.domain.EntityType;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.domain.MessageRule;
import org.avni.server.domain.User;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class MessageRuleServerEntityContractTest {

    @Test
    public void shouldConvertContractToNewModel() {
        MessageRuleWebContract messageRuleContract = new MessageRuleWebContract();
        String name = "Rule 1.2.3";
        messageRuleContract.setName(name);
        messageRuleContract.setMessageRule("messageRule");
        messageRuleContract.setScheduleRule("scheduleRule");
        messageRuleContract.setEntityType("ProgramEncounter");
        messageRuleContract.setEntityTypeId(1L);
        messageRuleContract.setMessageTemplateId("2");
        MessageRule messageRule = messageRuleContract.toModel(null);

        assertEquals(messageRule.getEntityType(), EntityType.ProgramEncounter);
        assertEquals(messageRule.getName(), name);
        assertNotNull(messageRule.getUuid());
        assertNull(messageRule.getId());
    }

    @Test
    public void shouldNotFailForMissingFields() {
        new MessageRuleWebContract().toModel(null);
    }

    @Test
    public void shouldSetVoidedToFalseByDefault() {
        MessageRule messageRule = new MessageRuleWebContract().toModel(null);

        assertFalse(messageRule.isVoided());
    }

    @Test
    public void shouldUseExistingModelIfProvided() {
        MessageRule messageRule = new MessageRule();
        MessageRule result = new MessageRuleWebContract().toModel(messageRule);

    }

    @Test
    public void extractParams() {
        MessageRule messageRule = new MessageRule();
        messageRule.setMessageRule(" 'use strict';\n" +
                "({params, imports}) => {\n" +
                "  const individual = params.entity;\n" +
                "  return {" +
                "    parameters: ['testing message to subject', '9876', 'two hours']" +
                "  }\n" +
                "};");
        messageRule.setMessageTemplateId("1234");
        MessageRequest msgRequest = new MessageRequest();
        User abc = User.newUser("abc", 1l);
        msgRequest.setCreatedBy(abc);
        msgRequest.setLastModifiedBy(abc);
        msgRequest.setMessageRule(messageRule);
        MessageRequestResponse response = MessageRequestResponse.fromMessageRequest(msgRequest, new ArrayList<>());
        assertEquals("[testing message to subject, 9876, two hours]", response.getMessageRuleParams());
    }
}