package org.avni.server.domain.metabase;

//Refer Documentation : https://www.metabase.com/docs/latest/api/card#post-apicard
public enum CardType {
    QUESTION("question");

    private final String type;

    CardType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
