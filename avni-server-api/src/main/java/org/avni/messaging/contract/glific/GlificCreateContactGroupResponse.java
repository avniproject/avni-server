package org.avni.messaging.contract.glific;

import java.util.List;

public class GlificCreateContactGroupResponse {
    private CreateGroupErrors createGroup;

    public CreateGroupErrors getCreateGroup() {
        return createGroup;
    }

    public void setCreateGroup(CreateGroupErrors createGroup) {
        this.createGroup = createGroup;
    }

    public boolean hasErrors() {
        return createGroup != null && createGroup.getErrors() != null && createGroup.getErrors().size() > 0;
    }

    public String getFirstError() {
        if (hasErrors())
            return createGroup.getErrors().get(0).getMessage();
        return null;
    }

    public static class CreateGroupErrors {
        private List<GlificResponseBodyError> errors;

        public List<GlificResponseBodyError> getErrors() {
            return errors;
        }

        public void setErrors(List<GlificResponseBodyError> errors) {
            this.errors = errors;
        }
    }

    public static class GlificResponseBodyError {
        private String key;
        private String message;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
