package org.avni.server.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a method that returns a saved visit, for hooks that act on saved health records after it returns.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HealthRecordSaved {
}
