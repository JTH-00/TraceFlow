package com.example.traceflow;

import com.example.traceflow.server.TraceFlowWebServer;

public class Main {
    public static void main(String[] args) {
        System.out.println("TraceFlow 서버가 실행 중입니다: http://localhost:8081");
        TraceFlowWebServer.start();
    }
}