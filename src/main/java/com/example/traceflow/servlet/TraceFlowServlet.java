package com.example.traceflow.servlet;

import com.example.traceflow.store.TraceStore;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.gradle.internal.impldep.com.google.gson.Gson;

import java.io.IOException;

public class TraceFlowServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        String json = new Gson().toJson(TraceStore.getTraces());
        resp.getWriter().write(json);
    }
}