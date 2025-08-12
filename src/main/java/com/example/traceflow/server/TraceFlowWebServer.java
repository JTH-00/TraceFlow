package com.example.traceflow.server;

import com.example.traceflow.servlet.FrontPageServlet;
import com.example.traceflow.servlet.TraceFlowServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TraceFlowWebServer {
    public static void start() {
        try {
            int port = loadPortFromProperties();
            Server server = new Server(port);

            // ServletContextHandler 생성
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);

            // 서블릿 등록
            context.addServlet(FrontPageServlet.class, "/");
            context.addServlet(TraceFlowServlet.class, "/logs");


            server.start();
            System.out.println("[TraceFlowWebServer] Started on port " + port);
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int loadPortFromProperties() {
        Properties props = new Properties();
        try (InputStream input = TraceFlowWebServer.class
                .getClassLoader()
                .getResourceAsStream("META-INF/gradle-plugins/application.properties")) {

            if (input == null) {
                throw new RuntimeException("application.properties not found");
            }

            props.load(input);
            String portStr = props.getProperty("traceflow.web.port", "8081");
            return Integer.parseInt(portStr);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }
}
