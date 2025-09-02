package com.example.traceflow.context;

import com.example.traceflow.store.TraceStore;
import com.example.traceflow.vo.TraceEntry;

import java.util.*;

public class TraceContext {
    private static final ThreadLocal<Deque<String>> callStack = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<List<TraceEntry>> currentSessionEntries = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<String> sessionId = ThreadLocal.withInitial(() -> UUID.randomUUID().toString());

    public static String getSessionId() {
        return sessionId.get();
    }

    public static void pushCall(String id) {
        callStack.get().push(id);
    }

    public static String popCall() {
        return callStack.get().pop();
    }

    public static String peekCall() {
        return callStack.get().peek();
    }

    public static boolean isStackEmpty() { return callStack.get().isEmpty(); }

    public static void addEntry(TraceEntry entry) { currentSessionEntries.get().add(entry); }
    public static List<TraceEntry> getCurrentSessionEntries() { return currentSessionEntries.get(); }

    public static void flush() {
        TraceStore.addTraces(currentSessionEntries.get());
        currentSessionEntries.set(new ArrayList<>());
        callStack.set(new ArrayDeque<>());
        sessionId.set(UUID.randomUUID().toString());
    }
}