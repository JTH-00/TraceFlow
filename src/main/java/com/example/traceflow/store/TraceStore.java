package com.example.traceflow.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TraceStore {
    private static final List<String> logs = Collections.synchronizedList(new ArrayList<>());

    public static void addLog(String log) {
        logs.add(log);
    }

    public static List<String> getLogs() {
        return new ArrayList<>(logs);
    }
}