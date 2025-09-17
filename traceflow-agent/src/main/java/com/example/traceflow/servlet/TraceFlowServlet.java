package com.example.traceflow.servlet;

import com.example.traceflow.store.TraceStore;
import com.example.traceflow.tree.TraceTreeBuilder;
import com.example.traceflow.tree.TraceTreeNode;
import com.example.traceflow.vo.TraceEntry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.Gson;


import java.io.IOException;
import java.util.List;

public class TraceFlowServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");

        List<TraceEntry> entries = TraceStore.getTraces();
        List<TraceTreeNode> tree = TraceTreeBuilder.buildTree(entries);

        String json = new Gson().toJson(tree);
        resp.getWriter().write(json);
    }
}