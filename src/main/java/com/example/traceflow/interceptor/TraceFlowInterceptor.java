package com.example.traceflow.interceptor;

import com.example.traceflow.context.TraceContext;
import com.example.traceflow.vo.TraceEntry;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class TraceFlowInterceptor {
    public static Object intercept(@Origin Method method,
                                   @AllArguments Object[] args,
                                   @SuperCall Callable<?> callable) throws Exception {

        // 부모 ID는 push 전에 캡처
        String parentId = TraceContext.peekCall();
        String currentId = UUID.randomUUID().toString();
        TraceContext.pushCall(currentId);

        long start = System.currentTimeMillis();
        boolean async = false;
        boolean error = false;
        String errorType = null;
        String errorMessage = null;
        Object result = null;

        try {
            result = callable.call();

            // async check
            if (result instanceof CompletableFuture<?> || method.isAnnotationPresent(Async.class)) {
                async = true;
            }

        } catch (Throwable t) {
            error = true;
            errorType = t.getClass().getSimpleName();
            errorMessage = t.getMessage();
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - start;

            TraceEntry entry = new TraceEntry(
                currentId,
                parentId,
                TraceContext.getSessionId(),
                method.getDeclaringClass().getName(),
                method.getName(),
                method.getReturnType().getSimpleName(),
                start,
                duration,
                async,
                error,
                errorType,
                errorMessage
            );

            TraceContext.addEntry(entry);
            TraceContext.popCall();

            // 최상위 호출이 끝났다면 세션을 flush하여 저장소로 이동
            if (TraceContext.isStackEmpty()) {
                TraceContext.flush();
            }
        }
        return result;
    }
}