package com.example.traceflow.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FrontPageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("web/index.html")) {
            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "index.html not found");
                return;
            }
            resp.getWriter().write(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
