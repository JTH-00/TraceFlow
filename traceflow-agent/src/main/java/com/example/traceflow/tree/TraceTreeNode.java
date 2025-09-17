package com.example.traceflow.tree;

import com.example.traceflow.vo.TraceEntry;

import java.util.ArrayList;
import java.util.List;

public class TraceTreeNode {
    private String id;
    private String methodName;
    private String className;
    private String returnType;
    private boolean async;
    private boolean error;
    private String errorType;
    private String errorMessage;
    private long duration;
    private List<TraceTreeNode> children = new ArrayList<>();

    public TraceTreeNode(TraceEntry entry) {
        this.id = entry.getId();
        this.methodName = entry.getMethodName();
        this.className = entry.getClassName();
        this.returnType = entry.getReturnType();
        this.async = entry.isAsync();
        this.error = entry.isError();
        this.errorType = entry.getErrorType();
        this.errorMessage = entry.getErrorMessage();
        this.duration = entry.getDuration();
    }

    public void addChild(TraceTreeNode child) { children.add(child); }

    public List<TraceTreeNode> getChildren() { return children; }

}