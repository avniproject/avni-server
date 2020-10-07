package org.openchs.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.EntityModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface RestControllerResourceProcessor<T> {

    default EntityModel<T> process(EntityModel<T> t) {
        return t;
    }

    default PagedModel<EntityModel<T>> wrap(Page<T> page) {
        PagedModel.PageMetadata pageMetadata = new PagedModel.PageMetadata(page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages());
        List<EntityModel<T>> resources = new ArrayList<>();
        for (T it : page) resources.add(this.process(new EntityModel<>(it)));
        return new PagedModel<>(resources, pageMetadata);
    }

    default List<EntityModel<T>> wrap(List<T> list) {
        return list.stream().map(t -> this.process(new EntityModel<>(t))).collect(Collectors.toList());
    }

    default PagedModel<EntityModel<T>> empty(Pageable pageable) {
        PagedModel.PageMetadata pageMetadata = new PagedModel.PageMetadata(pageable.getPageSize(), 0, 0, 0);
        return new PagedModel<>(new ArrayList<>(), pageMetadata);
    }
}
