package com.example.traceflow.interceptor;

import com.example.traceflow.context.TraceContext;
import com.example.traceflow.vo.TraceEntry;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Callable;

public class EntryPointInterceptor {

    @RuntimeType
    public static Object intercept(@Origin Method method,
                                   @AllArguments Object[] args,
                                   @SuperCall Callable<?> callable) throws Exception {

        // 새 추적 세션 시작
        String sessionId = UUID.randomUUID().toString();
        String rootId = UUID.randomUUID().toString();

        TraceContext.startNewSession(sessionId);
        TraceContext.enableTracing();
        TraceContext.pushCall(rootId);

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;

        try {
            System.out.println("[EntryPoint] Starting trace: " +
                method.getDeclaringClass().getSimpleName() + "." + method.getName());

            result = callable.call();

        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            TraceEntry entry = new TraceEntry(
                rootId, null, sessionId,
                method.getDeclaringClass().getName(),
                method.getName(),
                method.getReturnType().getSimpleName(),
                startTime, duration, false,
                error != null,
                error != null ? error.getClass().getSimpleName() : null,
                error != null ? error.getMessage() : null
            );

            TraceContext.addEntry(entry);
            TraceContext.popCall();
            TraceContext.disableTracing();
            TraceContext.flush();

            System.out.println("[EntryPoint] Trace completed: " + duration + "ms");
        }

        return result;
    }
}