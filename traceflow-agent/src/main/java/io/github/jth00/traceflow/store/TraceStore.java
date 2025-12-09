package io.github.jth00.traceflow.store;

import io.github.jth00.traceflow.vo.TraceEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe storage for trace data
 * Manages trace entries organized by session
 */
public class TraceStore {
    // Session-based data storage
    private static final Map<String, List<TraceEntry>> sessionData = new ConcurrentHashMap<>();
    private static final Set<String> completedSessions = ConcurrentHashMap.newKeySet();

    /**
     * Add trace entries for a session
     * @param entries List of trace entries to add
     */
    public static void addTraces(List<TraceEntry> entries) {
        if (entries.isEmpty()) return;

        String sessionId = entries.get(0).getSessionId();
        sessionData.put(sessionId, new ArrayList<>(entries));
        completedSessions.add(sessionId);
    }

    /**
     * Get all trace entries (for backward compatibility)
     * @return Combined list of all trace entries
     */
    public static List<TraceEntry> getTraces() {
        List<TraceEntry> allEntries = new ArrayList<>();
        sessionData.values().forEach(allEntries::addAll);
        return allEntries;
    }

    /**
     * Get trace entries for a specific session
     * @param sessionId Session identifier
     * @return List of trace entries for the session
     */
    public static List<TraceEntry> getTracesBySession(String sessionId) {
        return sessionData.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * Get all completed session IDs
     * @return Set of completed session IDs
     */
    public static Set<String> getCompletedSessions() {
        return new HashSet<>(completedSessions);
    }

    /**
     * Get session summary (session ID -> entry count)
     * @return Map of session IDs to entry counts
     */
    public static Map<String, Integer> getSessionSummary() {
        Map<String, Integer> summary = new HashMap<>();
        sessionData.forEach((id, entries) -> {
            summary.put(id, entries.size());
        });
        return summary;
    }
}
