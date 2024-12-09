package org.avni.server.domain;

import jakarta.persistence.*;

@Entity
public class UserSubject extends OrganisationAwareEntity {
    @ManyToOne
    private User user;
    @ManyToOne
    private Individual subject;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Individual getSubject() {
        return subject;
    }

    public void setSubject(Individual subject) {
        this.subject = subject;
    }
}
