package com.example.traceflow.vo;

public class TraceEntry {
    private final String id;
    private final String parentId;
    private final String className;
    private final String methodName;
    private final String returnType;
    private final long startTime;
    private final long duration;
    private final boolean async;
    private final boolean error;
    private final String errorType;
    private final String errorMessage;

    public TraceEntry(String id, String parentId, String className, String methodName, String returnType,
                      long startTime, long duration, boolean async, boolean error, String errorType, String errorMessage) {
        this.id = id;
        this.parentId = parentId;
        this.className = className;
        this.methodName = methodName;
        this.returnType = returnType;
        this.startTime = startTime;
        this.duration = duration;
        this.async = async;
        this.error = error;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReturnType() {
        return returnType;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isAsync() {
        return async;
    }

    public boolean isError() {
        return error;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
