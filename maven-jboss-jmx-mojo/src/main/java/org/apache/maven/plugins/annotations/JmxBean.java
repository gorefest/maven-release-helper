package org.apache.maven.plugins.annotations;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(CLASS)
@Target(ElementType.TYPE)
public @interface JmxBean {
    public String value();
    public String description() default "";
}
