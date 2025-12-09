package io.github.jth00.traceflow.vo;

import io.github.jth00.traceflow.enums.MethodTypeEnum;

import java.util.List;

/**
 * Represents a single method execution trace entry
 * Contains all information about method call including timing, parameters, and errors
 */
public class TraceEntry {
    private final String id;
    private final String parentId;
    private final String sessionId;
    private final String className;
    private final String methodName;
    private final String returnType;
    private final List<String> parameterTypes;
    private final long startTime;
    private final long duration;
    private final boolean async;
    private final boolean error;
    private final String errorType;
    private final String errorMessage;
    private final String stackTrace;
    private MethodTypeEnum methodType;

    public TraceEntry(String id, String parentId, String sessionId,
                      String className, String methodName, String returnType,
                      List<String> parameterTypes,
                      long startTime, long duration, boolean async,
                      boolean error, String errorType, String errorMessage,
                      String stackTrace,
                      MethodTypeEnum methodType) {
        this.id = id;
        this.parentId = parentId;
        this.sessionId = sessionId;
        this.className = className;
        this.methodName = methodName;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.startTime = startTime;
        this.duration = duration;
        this.async = async;
        this.error = error;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
        this.methodType = methodType;
    }

    // Getters
    public String getSessionId() { return sessionId; }
}