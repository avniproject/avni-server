package org.avni.server.importer.batch.model;

import org.springframework.batch.core.JobParameter;

import java.io.Serializable;

public class CustomJobParameter<T extends Serializable> extends JobParameter {
    private final T customParam;
    public CustomJobParameter(T customParam){
        super("");
        this.customParam = customParam;
    }
    public T getValue(){
        return customParam;
    }
}