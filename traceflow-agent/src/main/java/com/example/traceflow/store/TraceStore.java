package com.example.traceflow.store;

import com.example.traceflow.vo.TraceEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TraceStore {
    // 세션별로 데이터 관리
    private static final Map<String, List<TraceEntry>> sessionData = new ConcurrentHashMap<>();
    private static final Set<String> completedSessions = ConcurrentHashMap.newKeySet();

    public static void addTraces(List<TraceEntry> entries) {
        if (entries.isEmpty()) return;

        String sessionId = entries.get(0).getSessionId();
        sessionData.put(sessionId, new ArrayList<>(entries));
        completedSessions.add(sessionId);
    }

    // 전체 데이터 (호환성 유지)
    public static List<TraceEntry> getTraces() {
        List<TraceEntry> allEntries = new ArrayList<>();
        sessionData.values().forEach(allEntries::addAll);
        return allEntries;
    }

    // 특정 세션 데이터
    public static List<TraceEntry> getTracesBySession(String sessionId) {
        return sessionData.getOrDefault(sessionId, new ArrayList<>());
    }

    // 완료된 세션 목록
    public static Set<String> getCompletedSessions() {
        return new HashSet<>(completedSessions);
    }

    // 새로운 세션만 반환
    public static Map<String, Integer> getSessionSummary() {
        Map<String, Integer> summary = new HashMap<>();
        sessionData.forEach((id, entries) -> {
            summary.put(id, entries.size());
        });
        return summary;
    }
}
