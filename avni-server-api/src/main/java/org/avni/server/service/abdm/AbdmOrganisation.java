package org.avni.server.service.abdm;

// An organisation ABDM work is done for with no Avni user, and the system user from its Eka settings it is done as.
public record AbdmOrganisation(long id, String name, String systemUser) {
    @Override
    public String toString() {
        return "organisation " + id + (name == null ? "" : " (" + name + ")");
    }
}
