package org.avni.server.web.response.slice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import org.springframework.hateoas.Resources;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO to implement binding response representations of sliceable collections.
 *
 */
public class SlicedResources<T> extends Resources<T> {

    public static SlicedResources<?> NO_PAGE = new SlicedResources<Object>();

    private SlicedResources.SliceMetadata metadata;

    /**
     * Default constructor to allow instantiation by reflection.
     */
    public SlicedResources() {
        this(new ArrayList<T>(), null);
    }

    /**
     * Creates a new {@link SlicedResources} from the given content, {@link SlicedResources.SliceMetadata} and {@link Link}s (optional).
     *
     * @param content must not be {@literal null}.
     * @param metadata
     * @param links
     */
    public SlicedResources(Collection<T> content, SlicedResources.SliceMetadata metadata, Link... links) {
        this(content, metadata, Arrays.asList(links));
    }

    /**
     * Creates a new {@link SlicedResources} from the given content {@link SlicedResources.SliceMetadata} and {@link Link}s.
     *
     * @param content must not be {@literal null}.
     * @param metadata
     * @param links
     */
    public SlicedResources(Collection<T> content, SlicedResources.SliceMetadata metadata, Iterable<Link> links) {
        super(content, links);
        this.metadata = metadata;
    }

    /**
     * Returns the slice metadata.
     *
     * @return the metadata
     */
    @JsonProperty("slice")
    public SlicedResources.SliceMetadata getMetadata() {
        return metadata;
    }

    /**
     * Factory method to easily create a {@link SlicedResources} instance from a set of entities and pagination metadata.
     *
     * @param content must not be {@literal null}.
     * @param metadata
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Resource<S>, S> SlicedResources<T> wrap(Iterable<S> content, SlicedResources.SliceMetadata metadata) {

        Assert.notNull(content, "Content must not be null!");
        ArrayList<T> resources = new ArrayList<T>();

        for (S element : content) {
            resources.add((T) new Resource<S>(element));
        }

        return new SlicedResources<T>(resources, metadata);
    }

    /**
     * Returns the Link pointing to the next page (if set).
     *
     * @return
     */
    @JsonIgnore
    public Link getNextLink() {
        return getLink(Link.REL_NEXT);
    }

    /**
     * Returns the Link pointing to the previous page (if set).
     *
     * @return
     */
    @JsonIgnore
    public Link getPreviousLink() {
        return getLink(Link.REL_PREVIOUS);
    }

    /*
     * (non-Javadoc)
     * @see ResourceSupport#toString()
     */
    @Override
    public String toString() {
        return String.format("PagedResource { content: %s, metadata: %s, links: %s }", getContent(), metadata, getLinks());
    }

    /*
     * (non-Javadoc)
     * @see Resources#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        SlicedResources<?> that = (SlicedResources<?>) obj;
        boolean metadataEquals = this.metadata == null ? that.metadata == null : this.metadata.equals(that.metadata);

        return metadataEquals ? super.equals(obj) : false;
    }

    /*
     * (non-Javadoc)
     * @see Resources#hashCode()
     */
    @Override
    public int hashCode() {

        int result = super.hashCode();
        result += this.metadata == null ? 0 : 31 * this.metadata.hashCode();
        return result;
    }

    /**
     * Value object for slice metadata.
     *
     */
    public static class SliceMetadata {

        @JsonProperty //
        private long size;

        @JsonProperty //
        private long number;

        @JsonProperty //
        private boolean hasNext;

        protected SliceMetadata() {}

        /**
         * Creates a new {@link SlicedModel.SliceMetadata} from the given size, and slice number.
         *
         * @param size must be greater or equal to zero.
         * @param number zero-indexed slice number, greater or equal to zero.
         */
        public SliceMetadata(long size, long number) {

            Assert.isTrue(size > -1, "Size must not be negative!");
            Assert.isTrue(number > -1, "Number must not be negative!");

            this.size = size;
            this.number = number;
        }

        public SliceMetadata(long size, long number, boolean hasNext) {
            Assert.isTrue(size > -1, "Size must not be negative!");
            Assert.isTrue(number > -1, "Number must not be negative!");

            this.size = size;
            this.number = number;
            this.hasNext = hasNext;
        }

        /**
         * Returns the requested size of the slice.
         *
         * @return the size a positive long.
         */
        public long getSize() {
            return size;
        }

        /**
         * Returns the number of the current slice.
         *
         * @return the number a positive long.
         */
        public long getNumber() {
            return number;
        }

        /**
         * Returns the number of the current slice.
         *
         * @return the number a positive long.
         */
        public boolean hasNext() {
            return hasNext;
        }


        /*
         * (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return String.format("Metadata: { number: %d, size %d, hasNext %s }", number, size, hasNext);
        }

        /*
         * (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(@Nullable Object obj) {

            if (this == obj) {
                return true;
            }

            if (obj == null || !obj.getClass().equals(getClass())) {
                return false;
            }

            SliceMetadata that = (SliceMetadata) obj;

            return super.equals(that) //
                    && Objects.equals(this.number, that.number) //
                    && Objects.equals(this.size, that.size)
                    && Objects.equals(this.hasNext, that.hasNext);
        }

        /*
         * (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), number, size);
        }
    }
}
