package com.flowledger.platform.event.bus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as a typed platform event consumer. Handlers may also register via {@link PlatformEventDispatcher#register}. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformEventHandler {
    String value();
}
