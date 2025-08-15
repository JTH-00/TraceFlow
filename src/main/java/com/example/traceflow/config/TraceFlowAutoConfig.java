package com.example.traceflow.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.net.ServerSocket;
import java.io.IOException;

@Configuration
public class TraceFlowAutoConfig {
    
    @PostConstruct
    public void init() {
        // 포트가 사용 가능한지 확인
        if (isPortAvailable(8081)) {
            startWebServerIfJettyAvailable();
        } else {
            System.out.println("[TraceFlow] Port 8081 is already in use. Web server not started.");
        }
    }
    
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private void startWebServerIfJettyAvailable() {
        try {
            // Jetty 클래스가 사용 가능한지 확인
            Class.forName("org.eclipse.jetty.server.Server");
            
            // TraceFlowWebServer 클래스 로드 및 실행
            Class<?> webServerClass = Class.forName("com.example.traceflow.server.TraceFlowWebServer");
            java.lang.reflect.Method startMethod = webServerClass.getMethod("start");
            
            // 별도 스레드에서 웹서버 시작
            Thread webServerThread = new Thread(() -> {
                try {
                    startMethod.invoke(null);
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