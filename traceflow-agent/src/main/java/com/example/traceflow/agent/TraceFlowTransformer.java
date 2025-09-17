package com.example.traceflow.agent;

import com.example.traceflow.anotation.TraceFlow;
import com.example.traceflow.interceptor.TraceFlowInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class TraceFlowTransformer {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[TraceFlow Agent] Starting instrumentation...");

        // 1. 먼저 @TraceFlow가 붙은 진입점 메서드만 변환
        installEntryPointTransformer(inst);

        // 2. 그 다음 모든 애플리케이션 클래스의 메서드를 추적 가능하도록 변환
        installUniversalTransformer(inst);

        System.out.println("[TraceFlow Agent] Instrumentation installed successfully");
    }

    // @TraceFlow 진입점 변환기
    private static void installEntryPointTransformer(Instrumentation inst) {
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .ignore(getIgnoreMatcher())
            .type(
                // @TraceFlow 어노테이션이 붙은 클래스 또는 메서드를 가진 클래스
                isAnnotatedWith(TraceFlow.class)
                    .or(declaresMethod(isAnnotatedWith(TraceFlow.class)))
            )
            .transform(new EntryPointTransformer())
            .installOn(inst);
    }

    // 모든 애플리케이션 메서드 추적 변환기
    private static void installUniversalTransformer(Instrumentation inst) {
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .ignore(getIgnoreMatcher())
            .type(
                // 사용자 애플리케이션 패키지 (설정 가능하도록)
                nameStartsWith("com.")
                    .or(nameStartsWith("org."))
                    .and(not(nameStartsWith("org.springframework")))
                    .and(not(nameStartsWith("org.apache")))
                    .and(not(nameStartsWith("org.eclipse")))
                    .and(not(nameStartsWith("com.example.traceflow")))
                    .and(not(nameStartsWith("com.sun")))
                    .and(not(nameStartsWith("com.google")))
            )
            .transform(new UniversalMethodTransformer())
            .installOn(inst);
    }

    private static ElementMatcher<TypeDescription> getIgnoreMatcher() {
        return nameStartsWith("net.bytebuddy")
            .or(nameStartsWith("java."))
            .or(nameStartsWith("javax."))
            .or(nameStartsWith("sun."))
            .or(nameStartsWith("jdk."))
            .or(nameStartsWith("com.example.traceflow.interceptor"))
            .or(nameStartsWith("com.example.traceflow.context"))
            .or(nameStartsWith("com.example.traceflow.vo"))
            .or(nameStartsWith("com.example.traceflow.store"));
    }

    // @TraceFlow 진입점 변환기
    static class EntryPointTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                TypeDescription typeDescription,
                                                ClassLoader classLoader,
                                                JavaModule javaModule,
                                                ProtectionDomain protectionDomain) {

            System.out.println("[TraceFlow] Processing entry points in: " + typeDescription.getName());

            boolean classHasTraceFlow = typeDescription.getDeclaredAnnotations().isAnnotationPresent(TraceFlow.class);

            ElementMatcher<MethodDescription> methodMatcher;

            if (classHasTraceFlow) {
                // 클래스 레벨 @TraceFlow: 모든 public 메서드가 진입점
                methodMatcher = isPublic()
                    .and(not(isConstructor()))
                    .and(not(isStatic()))
                    .and(not(isSynthetic()))
                    .and(not(isAbstract()));
            } else {
                // 메서드 레벨 @TraceFlow: 해당 메서드만 진입점
                methodMatcher = isAnnotatedWith(TraceFlow.class)
                    .and(isPublic());
            }

            return builder.method(methodMatcher)
                .intercept(MethodDelegation.to(TraceFlowInterceptor.class));
        }
    }

    // 모든 메서드 추적 변환기 (추적이 활성화되었을 때만 동작)
    static class UniversalMethodTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                TypeDescription typeDescription,
                                                ClassLoader classLoader,
                                                JavaModule javaModule,
                                                ProtectionDomain protectionDomain) {

            // 모든 메서드 추적 (private, protected 포함)
            ElementMatcher<MethodDescription> methodMatcher =
                not(isConstructor())
                    .and(not(isStatic()))
                    .and(not(isSynthetic()))
                    .and(not(isBridge()))
                    .and(not(isNative()))
                    .and(not(isAbstract()))
                    .and(not(named("toString")))
                    .and(not(named("hashCode")))
                    .and(not(named("equals")))
                    .and(not(named("clone")))
                    .and(not(named("finalize")))
                    .and(not(named("getClass")))
                    .and(not(nameStartsWith("lambda$")))
                    .and(not(nameStartsWith("access$")));  // 내부 접근자 메서드 제외

            return builder.method(methodMatcher)
                .intercept(MethodDelegation.to(TraceFlowInterceptor.class));
        }
    }
}