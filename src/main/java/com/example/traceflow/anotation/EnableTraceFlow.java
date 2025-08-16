package com.example.traceflow.anotation;

import com.example.traceflow.config.TraceFlowAutoConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(TraceFlowAutoConfig.class)
public @interface EnableTraceFlow {
    int port() default 8081;
}