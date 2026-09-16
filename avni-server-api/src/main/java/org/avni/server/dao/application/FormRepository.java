package org.avni.server.dao.application;

import org.avni.server.application.Form;
import org.avni.server.application.FormType;
import org.avni.server.dao.FindByLastModifiedDateTime;
import org.avni.server.dao.ReferenceDataRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "form", path = "form")
public interface FormRepository extends ReferenceDataRepository<Form>, FindByLastModifiedDateTime<Form>, JpaSpecificationExecutor<Form> {
    @Query("select f.name from Form f where f.isVoided = false")
    List<String> getAllNames();

    List<Form> findByFormTypeAndIsVoidedFalse(FormType formType);

    /**
     * Forms are the only metadata entity with no uniqueness on the name: subject types, programmes,
     * encounter types, catchments, address level types, dashboards, cards and news all refuse a repeat,
     * and two forms in one organisation can be named identically today.
     *
     * Voided forms are excluded deliberately. Voiding renames a form to "<name> (voided~<id>)", so the
     * original name is freed and an administrator who deletes a form must be able to use its name again.
     * Row level security scopes this to the caller's organisation, so no organisation predicate is needed.
     */
    List<Form> findByNameIgnoreCaseAndIsVoidedFalse(String name);
}
