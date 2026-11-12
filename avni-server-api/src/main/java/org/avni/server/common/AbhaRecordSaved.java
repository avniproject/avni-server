package org.avni.server.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a method that stores a record of an ABHA action from the phone, for hooks that act on it after it returns.
// Never on a @Transactional method: its hooks outrank the transaction advice, so they would run after that commit, outside it.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AbhaRecordSaved {
}
