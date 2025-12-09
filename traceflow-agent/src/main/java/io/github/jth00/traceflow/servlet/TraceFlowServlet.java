package io.github.jth00.traceflow.servlet;

import io.github.jth00.traceflow.store.TraceStore;
import io.github.jth00.traceflow.vo.TraceEntry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.Gson;


import java.io.IOException;
import java.util.*;

/**
 * REST API servlet for trace data
 * Provides endpoints for fetching sessions and trace entries
 */
public class TraceFlowServlet extends HttpServlet {
    private static final String ACTION_SESSIONS = "sessions";
    private static final String ACTION_NEW_SESSIONS = "new-sessions";

    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_COUNT = "count";
    private static final String KEY_NEW_SESSIONS = "newSessions";
    private static final String KEY_HAS_NEW = "hasNew";

    private static final Set<String> sentSessions = new HashSet<>();

    /**
     * Handle GET requests for trace data
     * Supports three modes:
     * 1. ?action=sessions - Get all session IDs
     * 2. ?action=new-sessions - Get only new session IDs
     * 3. ?sessionId=xxx - Get trace data for specific session
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache");

        String action = req.getParameter("action");
        String sessionId = req.getParameter("sessionId");
        Gson gson = new Gson();

        if (ACTION_SESSIONS.equals(action)) {
            // Return session list only
            Set<String> sessions = TraceStore.getCompletedSessions();
            Map<String, Object> response = new HashMap<>();
            response.put(KEY_SESSIONS, sessions);
            response.put(KEY_COUNT, sessions.size());
            resp.getWriter().write(gson.toJson(response));

        } else if (ACTION_NEW_SESSIONS.equals(action)) {
            // Return new sessions only
            Set<String> allSessions = TraceStore.getCompletedSessions();
            Set<String> newSessions = new HashSet<>(allSessions);
            newSessions.removeAll(sentSessions);
            sentSessions.addAll(newSessions);

            Map<String, Object> response = new HashMap<>();
            response.put(KEY_NEW_SESSIONS, newSessions);
            response.put(KEY_HAS_NEW, !newSessions.isEmpty());
            resp.getWriter().write(gson.toJson(response));

        } else if (sessionId != null) {
            // Return specific session data
            List<TraceEntry> entries = TraceStore.getTracesBySession(sessionId);
            resp.getWriter().write(gson.toJson(entries));

        } else {
            // Return all data (default)
            List<TraceEntry> entries = TraceStore.getTraces();
            resp.getWriter().write(gson.toJson(entries));
        }
    }
}