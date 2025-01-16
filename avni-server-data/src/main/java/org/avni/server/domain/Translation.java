package org.avni.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.framework.hibernate.JSONObjectUserType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "translation")
@BatchSize(size = 100)
public class Translation extends OrganisationAwareEntity {
    @Column
    @Type(value = JSONObjectUserType.class)
    private JsonObject translationJson;


    @NotNull
    @Column
    @Enumerated(EnumType.STRING)
    private Locale language;

    public Locale getLanguage() {
        return language;
    }

    public void setLanguage(Locale language) {
        this.language = language;
    }

    public JsonObject getTranslationJson() {
        return translationJson;
    }

    public void setTranslationJson(JsonObject translationJson) {
        this.translationJson = translationJson;
    }

}
