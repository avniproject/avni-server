package org.avni.server.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a method that stores a record of an ABHA action from the phone, for hooks that act on it after it returns.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AbhaRecordSaved {
}
