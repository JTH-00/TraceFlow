package io.github.jth00.traceflow.anotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class as an entry point for tracing
 *
 * When applied to a class, all public methods become entry points.
 * When applied to a method, only that method is an entry point.
 *
 * Entry points start new tracing sessions and capture all downstream method calls.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TraceFlow {
}