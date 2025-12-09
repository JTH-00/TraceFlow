package io.github.jth00.traceflow.server;

import io.github.jth00.traceflow.servlet.TraceFlowServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.ResourceFactory;

/**
 * Jetty web server for TraceFlow UI
 * Serves static resources and REST API for trace data
 */
public class TraceFlowWebServer {
    private static Server server;
    private static final String WEB_RESOURCE_DIR = "web";
    private static final String WELCOME_FILE = "index.html";
    private static final String LOGS_PATH = "/logs";

    /**
     * Start the web server
     * @param port Port number to listen on
     */
    public static void start(int port) {
        if (server != null && server.isRunning()) {
            return;
        }

        try {
            server = new Server(port);

            // Static resource handler
            ResourceHandler resourceHandler = new ResourceHandler();

            // Jetty 12 resource configuration
            ResourceFactory resourceFactory = ResourceFactory.of(resourceHandler);
            var webResource = resourceFactory.newClassLoaderResource(WEB_RESOURCE_DIR);

            resourceHandler.setBaseResource(webResource);
            resourceHandler.setDirAllowed(false);
            resourceHandler.setWelcomeFiles(WELCOME_FILE);

            // Servlet handler
            ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
            servletHandler.setContextPath("/");
            servletHandler.addServlet(TraceFlowServlet.class, LOGS_PATH);

            // Combine handlers
            Handler.Sequence handlers = new Handler.Sequence(
                resourceHandler,     // Check static files first
                servletHandler       // Then servlets
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