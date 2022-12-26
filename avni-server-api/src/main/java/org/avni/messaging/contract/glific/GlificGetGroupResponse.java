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
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
