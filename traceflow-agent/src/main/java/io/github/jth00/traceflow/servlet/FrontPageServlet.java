package io.github.jth00.traceflow.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FrontPageServlet extends HttpServlet {
    private static final String FRONT_PAGE_PATH = "web/index.html";
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(FRONT_PAGE_PATH)) {
            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "index.html not found");
                return;
            }
            resp.getWriter().write(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
