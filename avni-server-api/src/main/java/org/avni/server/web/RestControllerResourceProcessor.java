package org.avni.server.web;

import org.avni.server.web.response.slice.SlicedResources;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
        for (T it : page) resources.add(this.process(EntityModel.of(it)));
        return PagedModel.of(resources, pageMetadata);
    }

    default SlicedResources<EntityModel<T>> wrap(Slice<T> slice) {
        SlicedResources.SliceMetadata sliceMetadata = new SlicedResources.SliceMetadata(slice.getSize(), slice.getNumber(), slice.hasNext());
        List<EntityModel<T>> resources = new ArrayList<>();
        for (T it : slice) resources.add(this.process(EntityModel.of(it)));
        return new SlicedResources<>(resources, sliceMetadata);
    }

    default List<EntityModel<T>> wrap(List<T> list) {
        return list.stream().map(t -> this.process(EntityModel.of(t))).collect(Collectors.toList());
    }

    default PagedModel<EntityModel<T>> empty(Pageable pageable) {
        PagedModel.PageMetadata pageMetadata = new PagedModel.PageMetadata(pageable.getPageSize(), 0, 0, 0);
        return PagedModel.of(new ArrayList<>(), pageMetadata);
    }

    default PagedModel<T> wrapListAsPage(List list) {
        PagedModel.PageMetadata pageMetadata = new PagedModel.PageMetadata(list.size(), 0, list.size(), 1);
        return PagedModel.of(list, pageMetadata);
    }
}
