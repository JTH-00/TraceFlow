package io.github.jth00.traceflow.interceptor;

import io.github.jth00.traceflow.context.TraceContext;
import io.github.jth00.traceflow.vo.TraceEntry;
import io.github.jth00.traceflow.enums.MethodTypeEnum;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Interceptor for @TraceFlow entry point methods
 * Starts a new tracing session and captures the root method call
 */
public class EntryPointInterceptor {

    /** Maximum number of stack trace lines to capture when an error occurs */
    private static final int MAX_ERROR_STACKTRACE_LINES = 5;

    /**
     * Intercept method execution at entry point
     * @param method Original method being intercepted
     * @param callable Callable to invoke original method
     * @return Original method result
     * @throws Exception Any exception from original method
     */
    @RuntimeType
    public static Object intercept(@Origin Method method,
                                   @SuperCall Callable<?> callable) throws Exception {

        // Start new tracing session
        String sessionId = UUID.randomUUID().toString();
        String rootId = UUID.randomUUID().toString();

        TraceContext.startNewSession(sessionId);
        TraceContext.enableTracing();
        TraceContext.pushCall(rootId);

        // Extract parameter types
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
            String stackTrace = error != null ? getStackTraceString(error) : null;

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
                MethodTypeEnum.ENTRY_POINT
            );

            TraceContext.addEntry(entry);
            TraceContext.popCall();
            TraceContext.disableTracing();
            TraceContext.flush();

            System.out.println("[EntryPoint] Trace completed: " + duration + "ms");
        }

        return result;
    }

    /**
     * Convert exception stack trace to string (top N lines only)
     * @param throwable Exception to extract stack trace from
     * @return Stack trace as string (limited to first {@value #MAX_ERROR_STACKTRACE_LINES} lines)
     */
    private static String getStackTraceString(Throwable throwable) {
        if (throwable == null) return null;

        StackTraceElement[] elements = throwable.getStackTrace();
        int limit = Math.min(MAX_ERROR_STACKTRACE_LINES, elements.length);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append(elements[i].toString());
            if (i < limit - 1) sb.append("\n");
        }

        return sb.toString();
    }
}