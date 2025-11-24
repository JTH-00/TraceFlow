package com.example.traceflow.vo;

import java.util.List;

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
    private String methodType;

    public TraceEntry(String id, String parentId, String sessionId,
                      String className, String methodName, String returnType,
                      List<String> parameterTypes,
                      long startTime, long duration, boolean async,
                      boolean error, String errorType, String errorMessage,
                      String stackTrace,
                      String methodType) {
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
    public String getId() { return id; }
    public String getParentId() { return parentId; }
    public String getSessionId() { return sessionId; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getReturnType() { return returnType; }
    public List<String> getParameterTypes() { return parameterTypes; }
    public long getStartTime() { return startTime; }
    public long getDuration() { return duration; }
    public boolean isAsync() { return async; }
    public boolean isError() { return error; }
    public String getErrorType() { return errorType; }
    public String getErrorMessage() { return errorMessage; }
    public String getStackTrace() { return stackTrace; }
    public String getMethodType() { return methodType; }
    public void setMethodType(String methodType) { this.methodType = methodType; }
}