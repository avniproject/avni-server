package org.avni.server.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a method that returns a saved or voided subject, for hooks that keep identity links in step, inside its transaction.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubjectIdentitySaved {
}
