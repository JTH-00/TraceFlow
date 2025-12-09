
package io.github.jth00.traceflow.interceptor;

import io.github.jth00.traceflow.context.TraceContext;
import io.github.jth00.traceflow.enums.MethodTypeEnum;
import io.github.jth00.traceflow.vo.TraceEntry;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Interceptor for all traced methods
 * Only active when TraceContext is enabled
 */
public class TraceFlowInterceptor {

    /** Maximum number of stack trace lines to capture when an error occurs */
    private static final int MAX_ERROR_STACKTRACE_LINES = 5;

    // Object default methods
    private static final Set<String> OBJECT_METHODS = Set.of(
        "toString", "hashCode", "equals", "getClass"
    );

    // Lombok / Builder related methods
    private static final Set<String> LOMBOK_METHODS = Set.of(
        "builder", "build"
    );

    // Lambda / Async related prefixes
    private static final String LAMBDA_PREFIX = "lambda$";
    private static final String ACCESSOR_PREFIX = "access$";

    // Auxiliary / Proxy / CGLIB class identifiers
    private static final List<String> EXCLUDED_CLASS_PATTERNS = List.of(
        "$auxiliary$", "$$", "$Builder", "CGLIB", "Logger", "Log4j", "Slf4j"
    );

    private static final Set<String> ASYNC_METHOD_NAMES = Set.of(
        "call", "run"
    );

    // -------------------- Intercept --------------------

    /**
     * Intercept method execution for tracing
     * @param method Original method being intercepted
     * @param callable Callable to invoke original method
     * @return Original method result
     * @throws Exception Any exception from original method
     */
    @RuntimeType
    public static Object intercept(@Origin Method method,
                                   @SuperCall Callable<?> callable) throws Exception {

        if (!TraceContext.isTracingEnabled()) {
            return callable.call();
        }

        if (shouldSkipMethod(method)) {
            return callable.call();
        }

        String parentId = TraceContext.peekCall();
        String currentId = UUID.randomUUID().toString();
        String sessionId = TraceContext.getSessionId();

        if (parentId == null) {
            return callable.call();
        }

        List<String> parameterTypes = Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.toList());

        TraceContext.pushCall(currentId);

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        boolean isAsync = false;

        try {
            result = callable.call();

            // Handle async methods (CompletableFuture)
            if (result instanceof CompletionStage) {
                isAsync = true;
                CompletableFuture<?> future = result instanceof CompletableFuture ?
                    (CompletableFuture<?>) result : ((CompletionStage<?>) result).toCompletableFuture();

                final String capturedSessionId = sessionId;
                final String capturedParentId = parentId;
                final List<String> capturedParamTypes = parameterTypes;

                result = future.whenComplete((r, t) -> {
                    if (TraceContext.isTracingEnabledForSession(capturedSessionId)) {
                        long duration = System.currentTimeMillis() - startTime;
                        MethodTypeEnum methodType = classifyMethod(method);
                        String stackTrace = t != null ? getStackTraceString(t) : null;

                        TraceEntry asyncEntry = new TraceEntry(
                            currentId,
                            capturedParentId,
                            capturedSessionId,
                            method.getDeclaringClass().getName(),
                            method.getName(),
                            method.getReturnType().getSimpleName(),
                            capturedParamTypes,
                            startTime,
                            duration,
                            true,  // async
                            t != null,
                            t != null ? t.getClass().getSimpleName() : null,
                            t != null ? t.getMessage() : null,
                            stackTrace,
                            methodType
                        );

                        TraceContext.addEntryToSession(capturedSessionId, asyncEntry);
                    }
                });
            }

        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            if (!isAsync) {
                long duration = System.currentTimeMillis() - startTime;
                MethodTypeEnum methodType = classifyMethod(method);
                String stackTrace = error != null ? getStackTraceString(error) : null;

                TraceEntry entry = new TraceEntry(
                    currentId,
                    parentId,
                    sessionId,
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    method.getReturnType().getSimpleName(),
                    parameterTypes,
                    startTime,
                    duration,
                    false,  // sync
                    error != null,
                    error != null ? error.getClass().getSimpleName() : null,
                    error != null ? error.getMessage() : null,
                    stackTrace,
                    methodType
                );

                TraceContext.addEntry(entry);
            }

            TraceContext.popCall();
        }

        return result;
    }

    // -------------------- Method Classification --------------------

    /**
     * Classify method type (GETTER, SETTER, or BUSINESS)
     * @param method Method to classify
     * @return Method type enum
     */
    private static MethodTypeEnum classifyMethod(Method method) {
        String name = method.getName();
        int paramCount = method.getParameterCount();

        if ((name.startsWith("get") || name.startsWith("is")) && paramCount == 0) {
            return MethodTypeEnum.GETTER;
        }

        if (name.startsWith("set") && paramCount == 1) {
            return MethodTypeEnum.SETTER;
        }

        return MethodTypeEnum.BUSINESS;
    }

    // -------------------- Method Exclusion Filter --------------------

    /**
     * Check if method should be skipped from tracing
     * @param method Method to check
     * @return true if method should be skipped
     */
    private static boolean shouldSkipMethod(Method method) {
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();

        for (String pattern : EXCLUDED_CLASS_PATTERNS) {
            if (className.contains(pattern)) return true;
        }

        if (methodName.startsWith(LAMBDA_PREFIX) || methodName.startsWith(ACCESSOR_PREFIX)) {
            return true;
        }

        if (OBJECT_METHODS.contains(methodName) || LOMBOK_METHODS.contains(methodName) || ASYNC_METHOD_NAMES.contains(methodName)) {
            return true;
        }

        return false;
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