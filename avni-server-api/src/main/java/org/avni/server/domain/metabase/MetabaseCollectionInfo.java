package org.avni.server.domain.metabase;

public class MetabaseCollectionInfo {
    private String name;
    private int id;
    private boolean isPersonal;

    public MetabaseCollectionInfo(String name, int id, boolean isPersonal) {
        this.name = name;
        this.id = id;
        this.isPersonal = isPersonal;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public boolean isPersonal() {
        return isPersonal;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setPersonal(boolean personal) {
        isPersonal = personal;
    }
}
