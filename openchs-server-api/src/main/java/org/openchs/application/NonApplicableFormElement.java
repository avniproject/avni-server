package org.openchs.application;

import org.openchs.domain.OrganisationAwareEntity;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "non_applicable_form_element")
public class NonApplicableFormElement extends OrganisationAwareEntity {
    
}