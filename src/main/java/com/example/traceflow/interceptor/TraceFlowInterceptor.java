package com.example.traceflow.interceptor;

import com.example.traceflow.store.TraceStore;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class TraceFlowInterceptor {

    public static Object intercept(@net.bytebuddy.implementation.bind.annotation.Origin Method method,
                                   @net.bytebuddy.implementation.bind.annotation.AllArguments Object[] args,
                                   @net.bytebuddy.implementation.bind.annotation.SuperCall Callable<?> callable) throws Exception {
        long start = System.currentTimeMillis();
        String logStart = "▶ " + method.getDeclaringClass().getName() + "." + method.getName() + "() 호출";
        TraceStore.addLog(logStart);
        System.out.println("[TraceFlow] " + logStart);

        try {
            Object result = callable.call();
            long elapsed = System.currentTimeMillis() - start;
            String logSuccess = "✔ " + method.getDeclaringClass().getName() + "." + method.getName() + "() 완료 (" + elapsed + "ms)";
            TraceStore.addLog(logSuccess);
            System.out.println("[TraceFlow] " + logSuccess);
            return result;
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            String logError = "❌ " + method.getDeclaringClass().getName() + "." + method.getName() + "() 실패 (" + elapsed + "ms) : " + t.getMessage();
            TraceStore.addLog(logError);
            System.err.println("[TraceFlow] " + logError);
            throw t;
        }
    }
}