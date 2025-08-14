package com.example.traceflow.config;

import com.example.traceflow.server.TraceFlowWebServer;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TraceFlowAutoConfig {
    @PostConstruct
    public void init() {
        new Thread(TraceFlowWebServer::start).start();
        System.out.println("[TraceFlow] WebServer Started");
    }
}