package com.example.traceflow.interceptor;

import java.lang.reflect.Method;

public class TraceFlowInterceptor {

    public static Object intercept(@net.bytebuddy.implementation.bind.annotation.Origin Method method,
                                   @net.bytebuddy.implementation.bind.annotation.AllArguments Object[] args,
                                   @net.bytebuddy.implementation.bind.annotation.SuperCall java.util.concurrent.Callable<?> callable) throws Exception {
        long start = System.currentTimeMillis();
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();

        System.out.println("[TraceFlow] ▶ " + className + "." + methodName + " 호출 시작");

        try {
            Object result = callable.call();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[TraceFlow] ✔ " + className + "." + methodName + " 완료 (" + elapsed + "ms)");
            return result;
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            System.err.println("[TraceFlow] ❌ " + className + "." + methodName + " 실패 (" + elapsed + "ms)");
            throw t;
        }
    }
}