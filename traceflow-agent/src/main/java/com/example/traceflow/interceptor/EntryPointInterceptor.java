package com.example.traceflow.interceptor;

import com.example.traceflow.context.TraceContext;
import com.example.traceflow.vo.TraceEntry;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class EntryPointInterceptor {

    private static final String ENTRY_TYPE = "ENTRY_POINT";

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

        // 파라미터 타입 추출
        List<String> parameterTypes = Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.toList());

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
            String stackTrace = error != null ? getStackTraceString(error, 5) : null;

            TraceEntry entry = new TraceEntry(
                rootId,
                null,
                sessionId,
                method.getDeclaringClass().getName(),
                method.getName(),
                method.getReturnType().getSimpleName(),
                parameterTypes,
                startTime,
                duration,
                false,
                error != null,
                error != null ? error.getClass().getSimpleName() : null,
                error != null ? error.getMessage() : null,
                stackTrace,
                ENTRY_TYPE
            );

            TraceContext.addEntry(entry);
            TraceContext.popCall();
            TraceContext.disableTracing();
            TraceContext.flush();

            System.out.println("[EntryPoint] Trace completed: " + duration + "ms");
        }

        return result;
    }

    // 스택 트레이스를 문자열로 변환 (상위 N개만)
    private static String getStackTraceString(Throwable throwable, int maxLines) {
        if (throwable == null) return null;

        StackTraceElement[] elements = throwable.getStackTrace();
        int limit = Math.min(maxLines, elements.length);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append(elements[i].toString());
            if (i < limit - 1) sb.append("\n");
        }

        return sb.toString();
    }
}