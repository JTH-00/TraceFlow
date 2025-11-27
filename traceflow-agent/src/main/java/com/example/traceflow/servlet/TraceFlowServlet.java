package com.example.traceflow.servlet;

import com.example.traceflow.store.TraceStore;
import com.example.traceflow.vo.TraceEntry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.google.gson.Gson;


import java.io.IOException;
import java.util.*;

public class TraceFlowServlet extends HttpServlet {
    private static final String ACTION_SESSIONS = "sessions";
    private static final String ACTION_NEW_SESSIONS = "new-sessions";

    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_COUNT = "count";
    private static final String KEY_NEW_SESSIONS = "newSessions";
    private static final String KEY_HAS_NEW = "hasNew";

    private static final Set<String> sentSessions = new HashSet<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache");

        String action = req.getParameter("action");
        String sessionId = req.getParameter("sessionId");
        Gson gson = new Gson();

        if (ACTION_SESSIONS.equals(action)) {
            // 세션 목록만 반환
            Set<String> sessions = TraceStore.getCompletedSessions();
            Map<String, Object> response = new HashMap<>();
            response.put(KEY_SESSIONS, sessions);
            response.put(KEY_COUNT, sessions.size());
            resp.getWriter().write(gson.toJson(response));

        } else if (ACTION_NEW_SESSIONS.equals(action)) {
            // 새로운 세션만 반환
            Set<String> allSessions = TraceStore.getCompletedSessions();
            Set<String> newSessions = new HashSet<>(allSessions);
            newSessions.removeAll(sentSessions);
            sentSessions.addAll(newSessions);

            Map<String, Object> response = new HashMap<>();
            response.put(KEY_NEW_SESSIONS, newSessions);
            response.put(KEY_HAS_NEW, !newSessions.isEmpty());
            resp.getWriter().write(gson.toJson(response));

        } else if (sessionId != null) {
            // 특정 세션 데이터 반환
            List<TraceEntry> entries = TraceStore.getTracesBySession(sessionId);
            resp.getWriter().write(gson.toJson(entries));

        } else {
            // 전체 데이터 (기본)
            List<TraceEntry> entries = TraceStore.getTraces();
            resp.getWriter().write(gson.toJson(entries));
        }
    }
}