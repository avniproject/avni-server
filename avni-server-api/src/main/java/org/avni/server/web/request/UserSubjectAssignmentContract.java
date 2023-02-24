package org.avni.server.web.request;

import java.util.List;

public class UserSubjectAssignmentContract extends CHSRequest {
    private Long userId;
    private List<Long> subjectIds;

    public void setUserId(Long id) {
        this.userId = id;
    }

    public Long getUserId() {
        return userId;
    }

    public List<Long> getSubjectIds() {
        return subjectIds;
    }

    public void setSubjectIds(List<Long> subjectIds) {
        this.subjectIds = subjectIds;
    }

}
