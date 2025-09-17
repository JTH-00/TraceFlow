package com.example.traceflow.config;

import com.example.traceflow.anotation.EnableTraceFlow;
import com.example.traceflow.server.TraceFlowWebServer;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

@Configuration
public class TraceFlowAutoConfig implements ImportAware {
    static int port = 8081;
    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        Map<String, Object> attrs = importMetadata.getAnnotationAttributes(EnableTraceFlow.class.getName());
        if (attrs.containsKey("port")) {
            port = (Integer) attrs.get("port");
        }
    }

    @PostConstruct
    public void init() {
        // 포트가 사용 가능한지 확인
        if (isPortAvailable(port)) {
            startWebServerIfJettyAvailable(port);
        } else {
            System.out.println("[TraceFlow] Port " + port + " is already in use. Web server not started.");
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void startWebServerIfJettyAvailable(int port) {
        try {
            // Jetty 클래스가 사용 가능한지 확인
            Class.forName("org.eclipse.jetty.server.Server");

            // TraceFlowWebServer 클래스 로드 및 실행
            Class<?> webServerClass = Class.forName("com.example.traceflow.server.TraceFlowWebServer");
            Method startMethod = webServerClass.getMethod("start",int.class);

            // 별도 스레드에서 웹서버 시작
            Thread webServerThread = new Thread(() -> {
                try {
                    startMethod.invoke(null,port);
                } catch (Exception e) {
                    System.err.println("[TraceFlow] Error starting web server: " + e.getMessage());
                }
            });
            webServerThread.setDaemon(true);
            webServerThread.start();

            System.out.println("[TraceFlow] Web server started successfully");

        } catch (ClassNotFoundException e) {
            System.out.println("[TraceFlow] Jetty dependencies not available. Web server not started.");
            System.out.println("[TraceFlow] To enable web server, add Jetty dependencies to your project:");
            System.out.println("[TraceFlow]   implementation 'org.eclipse.jetty:jetty-server:12.0.12'");
            System.out.println("[TraceFlow]   implementation 'org.eclipse.jetty.ee10:jetty-ee10-servlet:12.0.12'");
        } catch (Exception e) {
            System.err.println("[TraceFlow] Error setting up web server: " + e.getMessage());
        }
    }
}