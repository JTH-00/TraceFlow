package com.example.traceflow.context;

import com.example.traceflow.store.TraceStore;
import com.example.traceflow.vo.TraceEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 추적 컨텍스트 관리
 * - 추적 활성화/비활성화 상태 관리
 * - 세션별 데이터 격리
 * - 스레드별 호출 스택 관리
 */
public class TraceContext {

    // 추적 활성화 상태 (ThreadLocal)
    private static final ThreadLocal<Boolean> tracingEnabled = ThreadLocal.withInitial(() -> false);

    // 현재 세션 ID
    private static final ThreadLocal<String> currentSessionId = ThreadLocal.withInitial(() -> null);

    // 호출 스택
    private static final ThreadLocal<Deque<String>> callStack = ThreadLocal.withInitial(ArrayDeque::new);

    // 세션별 데이터 저장소 (thread-safe)
    private static final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    private static Set<String> excludedPackages = new HashSet<>();

    static {
        // 기본 제외 패키지
        excludedPackages.add("java.");
        excludedPackages.add("javax.");
        excludedPackages.add("sun.");
        excludedPackages.add("com.sun.");
        excludedPackages.add("org.springframework.boot.");
        excludedPackages.add("org.springframework.context.");
        excludedPackages.add("org.springframework.beans.");
    }

    // 세션 데이터 클래스
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

    // === 추적 제어 메서드 ===

    /**
     * 추적 활성화
     */
    public static void enableTracing() {
        tracingEnabled.set(true);
    }

    /**
     * 추적 비활성화
     */
    public static void disableTracing() {
        tracingEnabled.set(false);
    }

    /**
     * 추적 활성화 상태 확인
     */
    public static boolean isTracingEnabled() {
        return Boolean.TRUE.equals(tracingEnabled.get());
    }

    /**
     * 특정 세션의 추적 활성화 상태 확인
     */
    public static boolean isTracingEnabledForSession(String sessionId) {
        SessionData session = sessions.get(sessionId);
        return session != null && session.active.get();
    }

    // === 세션 관리 메서드 ===

    /**
     * 새 세션 시작
     */
    public static void startNewSession(String sessionId) {
        currentSessionId.set(sessionId);
        sessions.put(sessionId, new SessionData(sessionId));
        callStack.set(new ArrayDeque<>());

        System.out.println("[TraceContext] New session started: " + sessionId);
    }

    /**
     * 세션 복원 (비동기 처리용)
     */
    public static void restoreSession(String sessionId) {
        currentSessionId.set(sessionId);
        SessionData session = sessions.get(sessionId);
        if (session != null) {
            enableTracing();
        }
    }

    /**
     * 현재 세션 ID 반환
     */
    public static String getSessionId() {
        return currentSessionId.get();
    }

    // === 호출 스택 관리 ===

    /**
     * 호출 스택에 추가
     */
    public static void pushCall(String callId) {
        callStack.get().push(callId);
    }

    /**
     * 호출 스택에서 제거
     */
    public static String popCall() {
        Deque<String> stack = callStack.get();
        return stack.isEmpty() ? null : stack.pop();
    }

    /**
     * 현재 호출 ID 확인 (제거하지 않음)
     */
    public static String peekCall() {
        Deque<String> stack = callStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * 스택이 비어있는지 확인
     */
    public static boolean isStackEmpty() {
        return callStack.get().isEmpty();
    }

    // === 엔트리 관리 ===

    /**
     * 현재 세션에 엔트리 추가
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
     * 특정 세션에 엔트리 추가 (비동기 처리용)
     */
    public static void addEntryToSession(String sessionId, TraceEntry entry) {
        SessionData session = sessions.get(sessionId);
        if (session != null) {
            session.entries.add(entry);
        }
    }

    /**
     * 세션 데이터를 스토어로 플러시
     */
    public static void flush() {
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            SessionData session = sessions.get(sessionId);
            if (session != null && !session.entries.isEmpty()) {
                // 스토어에 저장
                TraceStore.addTraces(new ArrayList<>(session.entries));

                // 세션 비활성화
                session.active.set(false);

                System.out.println("[TraceContext] Flushed " + session.entries.size() +
                    " entries for session: " + sessionId);

                // 일정 시간 후 세션 정리 (메모리 관리)
                scheduleSessionCleanup(sessionId);
            }
        }

        // ThreadLocal 정리
        clearThreadLocals();
    }

    /**
     * ThreadLocal 변수들 정리
     */
    private static void clearThreadLocals() {
        tracingEnabled.set(false);
        currentSessionId.set(null);
        callStack.set(new ArrayDeque<>());
    }

    /**
     * 세션 정리 스케줄링 (5분 후 제거)
     */
    private static void scheduleSessionCleanup(String sessionId) {
        new Thread(() -> {
            try {
                Thread.sleep(5 * 60 * 1000); // 5분 대기
                sessions.remove(sessionId);
                System.out.println("[TraceContext] Session cleaned up: " + sessionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // === 설정 메서드 ===
    public static void addExcludedPackage(String packagePrefix) {
        excludedPackages.add(packagePrefix);
    }

    public static boolean isPackageExcluded(String className) {
        for (String prefix : excludedPackages) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // === 디버깅 메서드 ===

    public static void printDebugInfo() {
        System.out.println("=== TraceContext Debug Info ===");
        System.out.println("Tracing enabled: " + isTracingEnabled());
        System.out.println("Current session: " + currentSessionId.get());
        System.out.println("Call stack size: " + callStack.get().size());
        System.out.println("Active sessions: " + sessions.size());
        System.out.println("================================");
    }
}