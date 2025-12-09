package io.github.jth00.traceflow.context;

import io.github.jth00.traceflow.store.TraceStore;
import io.github.jth00.traceflow.vo.TraceEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages tracing context for method execution
 * - Enables/disables tracing state
 * - Isolates data by session
 * - Manages thread-local call stacks
 */
public class TraceContext {

    // Tracing enabled state (ThreadLocal)
    private static final ThreadLocal<Boolean> tracingEnabled = ThreadLocal.withInitial(() -> false);

    // Current session ID
    private static final ThreadLocal<String> currentSessionId = ThreadLocal.withInitial(() -> null);

    // Call stack for tracking parent-child relationships
    private static final ThreadLocal<Deque<String>> callStack = ThreadLocal.withInitial(ArrayDeque::new);

    // Session data storage (thread-safe)
    private static final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    /**
     * Session data class
     */
    private static class SessionData {
        final String sessionId;
        final List<TraceEntry> entries;
        final AtomicBoolean active;
        final long startTime;

        SessionData(String sessionId) {
            this.sessionId = sessionId;
            this.entries = new CopyOnWriteArrayList<>();
            this.active = new AtomicBoolean(true);
            this.startTime = System.currentTimeMillis();
        }
    }

    // === Tracing Control Methods ===

    /**
     * Enable tracing for the current thread
     */
    public static void enableTracing() {
        tracingEnabled.set(true);
    }

    /**
     * Disable tracing for the current thread
     */
    public static void disableTracing() {
        tracingEnabled.set(false);
    }

    /**
     * Check if tracing is enabled for the current thread
     * @return true if tracing is enabled
     */
    public static boolean isTracingEnabled() {
        return Boolean.TRUE.equals(tracingEnabled.get());
    }

    /**
     * Check if tracing is enabled for a specific session
     * @param sessionId Session ID to check
     * @return true if session is active
     */
    public static boolean isTracingEnabledForSession(String sessionId) {
        SessionData session = sessions.get(sessionId);
        return session != null && session.active.get();
    }

    // === Session Management Methods ===

    /**
     * Start a new tracing session
     * @param sessionId Unique session identifier
     */
    public static void startNewSession(String sessionId) {
        currentSessionId.set(sessionId);
        sessions.put(sessionId, new SessionData(sessionId));
        callStack.set(new ArrayDeque<>());

        System.out.println("[TraceContext] New session started: " + sessionId);
    }

    /**
     * Get current session ID
     * @return Current session ID or null
     */
    public static String getSessionId() {
        return currentSessionId.get();
    }

    // === Call Stack Management ===

    /**
     * Push a call ID onto the stack
     * @param callId Unique call identifier
     */
    public static void pushCall(String callId) {
        callStack.get().push(callId);
    }

    /**
     * Pop a call ID from the stack
     * @return Popped call ID or null if empty
     */
    public static String popCall() {
        Deque<String> stack = callStack.get();
        return stack.isEmpty() ? null : stack.pop();
    }

    /**
     * Peek at the current call ID without removing it
     * @return Current call ID or null if empty
     */
    public static String peekCall() {
        Deque<String> stack = callStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * Add an entry to the current session
     * @param entry TraceEntry to add
     */
    public static void addEntry(TraceEntry entry) {
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            SessionData session = sessions.get(sessionId);
            if (session != null) {
                session.entries.add(entry);
            }
        }
    }

    /**
     * Add an entry to a specific session (for async processing)
     * @param sessionId Target session ID
     * @param entry TraceEntry to add
     */
    public static void addEntryToSession(String sessionId, TraceEntry entry) {
        SessionData session = sessions.get(sessionId);
        if (session != null) {
            session.entries.add(entry);
        }
    }

    /**
     * Flush session data to the store
     */
    public static void flush() {
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            SessionData session = sessions.get(sessionId);
            if (session != null && !session.entries.isEmpty()) {
                // Save to store
                TraceStore.addTraces(new ArrayList<>(session.entries));

                // Deactivate session
                session.active.set(false);

                System.out.println("[TraceContext] Flushed " + session.entries.size() +
                    " entries for session: " + sessionId);

                // Schedule cleanup after some time (memory management)
                scheduleSessionCleanup(sessionId);
            }
        }

        // Clear ThreadLocal variables
        clearThreadLocals();
    }

    /**
     * Clear ThreadLocal variables
     */
    private static void clearThreadLocals() {
        tracingEnabled.set(false);
        currentSessionId.set(null);
        callStack.set(new ArrayDeque<>());
    }

    /**
     * Schedule session cleanup (remove after 5 minutes)
     * @param sessionId Session ID to cleanup
     */
    private static void scheduleSessionCleanup(String sessionId) {
        new Thread(() -> {
            try {
                Thread.sleep(5 * 60 * 1000); // Wait 5 minutes
                sessions.remove(sessionId);
                System.out.println("[TraceContext] Session cleaned up: " + sessionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}