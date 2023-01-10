package org.avni.messaging.contract.glific;

public class GlificGetGroupResponse {
    private GlificGroupGroup group;

    public GlificGroupGroup getGroup() {
        return group;
    }

    public void setGroup(GlificGroupGroup group) {
        this.group = group;
    }

    public static class GlificGroupGroup {
        private GlificGroup group;

        public GlificGroup getGroup() {
            return group;
        }

        public void setGroup(GlificGroup group) {
            this.group = group;
        }
    }

    public static class GlificGroup {
        private String id;
        private String label;
        private String description;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
