package com.example.traceflow;


public class TraceFlowExtension {
    private boolean autoInject = true;
    private int webServerPort = 8081;
    private String packagePath;
    private boolean includeTests = false;
    private boolean debugMode = false;
    private String agentPath;

    // 기본 생성자 필수
    public TraceFlowExtension() {
    }

    public boolean isAutoInject() {
        return autoInject;
    }

    public void setAutoInject(boolean autoInject) {
        this.autoInject = autoInject;
    }

    public int getWebServerPort() {
        return webServerPort;
    }

    public void setWebServerPort(int port) {
        this.webServerPort = port;
    }

    public String getPackagePath() { return packagePath; }

    public void setPackagePath(String pkg) { this.packagePath = pkg; }

    public boolean isIncludeTests() {
        return includeTests;
    }

    public void setIncludeTests(boolean includeTests) {
        this.includeTests = includeTests;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public String getAgentPath() {
        return agentPath;
    }

    public void setAgentPath(String agentPath) {
        this.agentPath = agentPath;
    }
}