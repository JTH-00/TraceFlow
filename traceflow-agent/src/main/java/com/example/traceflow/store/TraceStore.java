package com.example.traceflow.store;

import com.example.traceflow.vo.TraceEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TraceStore {
    private static final List<TraceEntry> traces = Collections.synchronizedList(new ArrayList<>());

    public static void addTraces(List<TraceEntry> entries) {
        traces.addAll(entries);
    }

    public static List<TraceEntry> getTraces() {
        return new ArrayList<>(traces);
    }
}
