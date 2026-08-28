package com.kingsfarm.kingsfarmbackend.audit;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Drop this on a service method to have {@link AuditLoggingAspect} write a
 * system_logs row every time it returns successfully (never on exception —
 * an action that failed to happen isn't worth auditing as having happened).
 * {@code detail} is a plain Spring EL expression (no #{...} wrapper, same
 * convention as {@code @Cacheable}'s "key") evaluated against the method's
 * parameters by name plus a bound {@code #result} variable for the return
 * value — e.g. {@code "'New user: ' + #request.username()"}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    Mod module();

    String action();

    /** Spring EL expression producing the human-readable detail string; blank means no detail. */
    String detail() default "";

    LogType type() default LogType.ACTIVITY;
}
