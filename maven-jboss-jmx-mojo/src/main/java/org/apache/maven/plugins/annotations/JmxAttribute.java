package org.apache.maven.plugins.annotations;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(ElementType.METHOD)
public @interface JmxAttribute {
    public String value() default "";
//    public boolean readAccess() default true;
//    public boolean writeAccess() default true;
}
