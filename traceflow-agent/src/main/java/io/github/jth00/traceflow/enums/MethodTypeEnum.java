package io.github.jth00.traceflow.enums;

/**
 * Method classification types
 */
public enum MethodTypeEnum {
    GETTER,
    SETTER,
    ERROR,

    /** Business logic method */
    BUSINESS,

    /** Entry point method (annotated with @TraceFlow) */
    ENTRY_POINT
}
