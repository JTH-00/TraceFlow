package com.example.traceflow.server;

import com.example.traceflow.servlet.TraceFlowServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;

public class TraceFlowWebServer {
    private static Server server;
    private static final String WEB_RESOURCE_DIR = "web";
    private static final String WELCOME_FILE = "index.html";
    private static final String LOGS_PATH = "/logs";

    public static void start(int port) {
        if (server != null && server.isRunning()) {
            return;
        }

        try {
            server = new Server(port);

            // 정적 리소스 핸들러
            ResourceHandler resourceHandler = new ResourceHandler();

            // Jetty 12 리소스 설정
            ResourceFactory resourceFactory = ResourceFactory.of(resourceHandler);
            var webResource = resourceFactory.newClassLoaderResource(WEB_RESOURCE_DIR);

            resourceHandler.setBaseResource(webResource);
            resourceHandler.setDirAllowed(false);
            resourceHandler.setWelcomeFiles(WELCOME_FILE);

            // 서블릿 핸들러
            ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
            servletHandler.setContextPath("/");
            servletHandler.addServlet(TraceFlowServlet.class, LOGS_PATH);

            // 핸들러 조합 (순서 중요!)
            Handler.Sequence handlers = new Handler.Sequence(
                resourceHandler,     // 먼저 정적 파일 확인
                servletHandler       // 없으면 서블릿으로
            );

            server.setHandler(handlers);
            server.start();

            System.out.println("[TraceFlow] Web UI started at http://localhost:" + port);

        } catch (Exception e) {
            System.err.println("[TraceFlow] Failed to start web server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}