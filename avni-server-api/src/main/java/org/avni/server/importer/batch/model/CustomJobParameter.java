package org.avni.server.importer.batch.model;

import org.springframework.batch.core.JobParameter;

import java.io.Serializable;

public class CustomJobParameter<T extends Serializable> extends JobParameter<T> {
    private final T customParam;
    public CustomJobParameter(T customParam){
        super(customParam, (Class<T>) customParam.getClass());
        this.customParam = customParam;
    }
    public T getValue(){
        return customParam;
    }
}
