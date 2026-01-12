package io.github.jth00.traceflow;


/**
 * Gradle extension for TraceFlow configuration
 * Allows users to configure the plugin via build.gradle
 */
public class TraceFlowExtension {
    private boolean autoInject = true;
    private int webServerPort = 8081;
    private String packagePath;

    /**
     * Default constructor required by Gradle
     */
    public TraceFlowExtension() {
    }

    /**
     * Check if auto-injection is enabled
     * @return true if auto-injection is enabled
     */
    public boolean isAutoInject() {
        return autoInject;
    }

    /**
     * Enable or disable auto-injection
     * @param autoInject true to enable
     */
    public void setAutoInject(boolean autoInject) {
        this.autoInject = autoInject;
    }

    /**
     * Get web server port
     * @return Port number
     */
    public int getWebServerPort() {
        return webServerPort;
    }

    /**
     * Set web server port
     * @param port Port number
     */
    public void setWebServerPort(int port) {
        this.webServerPort = port;
    }

    /**
     * Get package path to instrument
     * @return Package path
     */
    public String getPackagePath() {
        return packagePath;
    }

    /**
     * Set package path to instrument
     * @param pkg Package path (e.g., "com.example.myapp")
     */
    public void setPackagePath(String pkg) {
        this.packagePath = pkg;
    }
}