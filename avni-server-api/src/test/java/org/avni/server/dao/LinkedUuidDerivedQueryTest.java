package org.avni.server.dao;

import org.avni.server.domain.Checklist;
import org.avni.server.domain.ChecklistItem;
import org.avni.server.domain.GroupSubject;
import org.junit.Test;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class LinkedUuidDerivedQueryTest {
    @Test
    public void derivedQueriesOnParentUuidFilterThroughTheParentNotTheLinkedUuidFormula() {
        assertEquals(List.of("groupSubject.uuid"), filteredPaths("findByGroupSubjectUuidOrderByLastModifiedDateTimeAscIdAsc", GroupSubject.class));
        assertEquals(List.of("memberSubject.uuid"), filteredPaths("findByMemberSubjectUuidOrderByLastModifiedDateTimeAscIdAsc", GroupSubject.class));
        assertEquals(List.of("programEnrolment.uuid", "checklistDetail.name"), filteredPaths("findByProgramEnrolmentUuidAndChecklistDetailName", Checklist.class));
        assertEquals(List.of("checklist.uuid", "checklistItemDetail.uuid"), filteredPaths("findByChecklistUuidAndChecklistItemDetailUuid", ChecklistItem.class));
    }

    private List<String> filteredPaths(String methodName, Class<?> domainClass) {
        return new PartTree(methodName, domainClass).getParts().stream().map(Part::getProperty).map(path -> path.toDotPath()).toList();
    }
}
