
package com.example.traceflow.interceptor;

import com.example.traceflow.context.TraceContext;
import com.example.traceflow.vo.TraceEntry;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 모든 하위 메서드를 추적하는 인터셉터
 * TraceContext가 활성화되어 있을 때만 동작
 */
public class TraceFlowInterceptor {

    @RuntimeType
    public static Object intercept(@Origin Method method,
                                   @AllArguments Object[] args,
                                   @SuperCall Callable<?> callable) throws Exception {

        // 추적이 비활성화되어 있으면 그냥 원본 메서드 실행
        if (!TraceContext.isTracingEnabled()) {
            return callable.call();
        }

        // 재귀 호출이나 불필요한 메서드 필터링
        if (shouldSkipMethod(method)) {
            return callable.call();
        }

        String parentId = TraceContext.peekCall();
        String currentId = UUID.randomUUID().toString();
        String sessionId = TraceContext.getSessionId();

        // 부모가 없으면 (진입점이 아닌데 부모가 없는 경우) 스킵
        if (parentId == null) {
            return callable.call();
        }

        TraceContext.pushCall(currentId);

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        boolean isAsync = false;

        try {
            // 메서드 실행
            result = callable.call();

            // 비동기 처리 확인
            if (result instanceof CompletableFuture || result instanceof CompletionStage) {
                isAsync = true;
                CompletableFuture<?> future = result instanceof CompletableFuture ?
                    (CompletableFuture<?>) result : ((CompletionStage<?>) result).toCompletableFuture();

                // 캡처된 컨텍스트
                final String capturedSessionId = sessionId;
                final String capturedParentId = parentId;

                // 비동기 완료 시 처리
                result = future.whenComplete((r, t) -> {
                    // 비동기 스레드에서도 추적이 활성화되어 있는지 확인
                    if (TraceContext.isTracingEnabledForSession(capturedSessionId)) {
                        long duration = System.currentTimeMillis() - startTime;

                        String methodType = classifyMethod(method);

                        TraceEntry asyncEntry = new TraceEntry(
                            currentId,
                            capturedParentId,
                            capturedSessionId,
                            method.getDeclaringClass().getName(),
                            method.getName(),
                            method.getReturnType().getSimpleName(),
                            startTime,
                            duration,
                            true,  // async
                            t != null,
                            t != null ? t.getClass().getSimpleName() : null,
                            t != null ? t.getMessage() : null,
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
            // 동기 호출인 경우에만 처리
            if (!isAsync) {
                long duration = System.currentTimeMillis() - startTime;

                String methodType = classifyMethod(method);

                TraceEntry entry = new TraceEntry(
                    currentId,
                    parentId,
                    sessionId,
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    method.getReturnType().getSimpleName(),
                    startTime,
                    duration,
                    false,  // sync
                    error != null,
                    error != null ? error.getClass().getSimpleName() : null,
                    error != null ? error.getMessage() : null,
                    methodType
                );

                TraceContext.addEntry(entry);
            }

            TraceContext.popCall();
        }

        return result;
    }

    private static String classifyMethod(Method method) {
        String name = method.getName();
        int paramCount = method.getParameterCount();

        // Getter 패턴
        if ((name.startsWith("get") || name.startsWith("is")) && paramCount == 0) {
            return "GETTER";
        }

        // Setter 패턴
        if (name.startsWith("set") && paramCount == 1) {
            return "SETTER";
        }

        // 그 외는 비즈니스 로직
        return "BUSINESS";
    }

    private static boolean shouldSkipMethod(Method method) {
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();

        // auxiliary 클래스 제외
        if (className.contains("$auxiliary$") ||
            className.contains("$$") ||
            className.contains("$Builder") ||
            className.contains("CGLIB")) {
            return true;
        }

        // 람다 메서드 제외
        if (methodName.startsWith("lambda$") ||
            methodName.equals("call") ||
            methodName.equals("run")) {
            return true;
        }

        // 기본 Object 메서드들 제외
        if (methodName.equals("toString") ||
            methodName.equals("hashCode") ||
            methodName.equals("equals") ||
            methodName.equals("getClass")) {
            return true;
        }

        // Lombok 생성 메서드 제외
        if (methodName.equals("builder") ||
            methodName.equals("build") ||
            methodName.startsWith("access$")) {
            return true;
        }

        // 로깅 관련 제외
        if (className.contains("Logger") ||
            className.contains("Log4j") ||
            className.contains("Slf4j")) {
            return true;
        }

        return false;
    }
}