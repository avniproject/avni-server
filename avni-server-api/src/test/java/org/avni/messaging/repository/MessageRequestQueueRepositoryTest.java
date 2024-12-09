package org.avni.messaging.repository;

import org.avni.messaging.domain.MessageRequest;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.joda.time.Duration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Sql(scripts = {"/test-data.sql"})
public class MessageRequestQueueRepositoryTest extends AbstractControllerIntegrationTest {

    @Autowired
    private MessageRequestQueueRepository messageRequestQueueRepository;

    @Test
    @Transactional
    public void shouldRetrieveUndeliveredMessageRequests() {
        Stream<MessageRequest> unsentMessages = messageRequestQueueRepository.findDueMessageRequests(Duration.standardDays(4));
        assertThat(unsentMessages.findFirst().get().getUuid()).isEqualTo("75925823-109f-41a5-89e3-9c719c88155d");
    }

    @Test
    @Transactional
    public void shouldNotRetrieveMessagesThatAreOlder() {
        Stream<MessageRequest> unsentMessages = messageRequestQueueRepository.findDueMessageRequests(Duration.standardHours(-1)); //Future date
        assertThat(unsentMessages.count()).isEqualTo(0);
    }
}
