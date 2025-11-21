package com.example.traceflow.agent;

import com.example.traceflow.anotations.TraceFlow;
import com.example.traceflow.interceptor.EntryPointInterceptor;
import com.example.traceflow.interceptor.TraceFlowInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class TraceFlowTransformer {
    private static int port = 8081;

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[TraceFlow Agent] Starting instrumentation...");

        String targetPackage = parsePackagePath(agentArgs);

        startWebServer(parsePort(agentArgs));
        installEntryPointTransformer(inst);
        installUniversalTransformer(inst, targetPackage);

        System.out.println("[TraceFlow Agent] Instrumentation installed successfully");
    }

    private static int parsePort(String agentArgs) {
        // agentArgs에서 포트 파싱
        if (agentArgs != null && agentArgs.contains("port=")) {
            String[] parts = agentArgs.split(",");
            for (String part : parts) {
                if (part.startsWith("port=")) {
                    try {
                        port = Integer.parseInt(part.substring("port=".length()));
                    } catch (NumberFormatException e) {
                        System.err.println("[TraceFlow] Invalid port: " + agentArgs);
                    }
                }
            }
        }
        return port;
    }

    private static String parsePackagePath(String agentArgs) {
        if (agentArgs != null && agentArgs.contains("package=")) {
            String[] parts = agentArgs.split(",");
            for (String part : parts) {
                if (part.startsWith("package=")) {
                    return part.substring("package=".length());
                }
            }
        }
        return null;
    }

    private static void startWebServer(int port) {
        // Jetty 서버 시작 (별도 스레드)
        final int finalPort = port;
        Thread serverThread = new Thread(() -> {
            try {
                // Jetty 클래스 확인
                Class.forName("org.eclipse.jetty.server.Server");

                // TraceFlowWebServer 시작
                Class<?> webServerClass = Class.forName("com.example.traceflow.server.TraceFlowWebServer");
                Method startMethod = webServerClass.getMethod("start", int.class);
                startMethod.invoke(null, finalPort);

            } catch (ClassNotFoundException e) {
                System.out.println("[TraceFlow] Jetty not available, web UI disabled");
            } catch (Exception e) {
                System.err.println("[TraceFlow] Failed to start web server: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        System.out.println("[TraceFlow] Web server starting on port " + finalPort);
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
    private static void installUniversalTransformer(Instrumentation inst, String targetPackage) {
        if (targetPackage == null || targetPackage.isEmpty()) {
            throw new IllegalArgumentException("[TraceFlow Agent] Package path is required. " +
                "Please specify package path in traceFlow configuration.");
        }

        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .ignore(getIgnoreMatcher())
            .type(
                // 사용자 애플리케이션 패키지 (설정 가능하도록)
                nameStartsWith(targetPackage)  // 사용자 코드만
                    .and(not(nameContains("$$")))  // 프록시 제외
                    .and(not(nameContains("CGLIB")))  // CGLIB 제외
            )
            .transform(new UniversalMethodTransformer())
            .installOn(inst);
    }

    private static ElementMatcher<TypeDescription> getIgnoreMatcher() {
        return nameStartsWith("net.bytebuddy")
            .or(nameStartsWith("java."))
            .or(nameStartsWith("javax."))
            .or(nameStartsWith("jakarta."))  // 추가
            .or(nameStartsWith("sun."))
            .or(nameStartsWith("jdk."))
            .or(nameStartsWith("org.springframework"))  // Spring 전체 제외
            .or(nameStartsWith("org.hibernate"))  // Hibernate 제외
            .or(nameStartsWith("com.mysql"))  // MySQL 드라이버 제외
            .or(nameStartsWith("com.zaxxer"))  // HikariCP 제외
            .or(nameStartsWith("com.example.traceflow"));
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
                .intercept(MethodDelegation.to(EntryPointInterceptor.class));
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
            // auxiliary 클래스는 변환하지 않음
            if (typeDescription.getName().contains("$auxiliary$") ||
                typeDescription.getName().contains("$$")) {
                return builder;
            }

            // 모든 메서드 추적 (private, protected 포함)
            ElementMatcher<MethodDescription> methodMatcher =
                not(isConstructor())
                    .and(not(isStatic()))
                    .and(not(isSynthetic()))
                    .and(not(isBridge()))
                    .and(not(isNative()))
                    .and(not(isAbstract()))
                    .and(not(isDeclaredBy(nameContains("$"))))
                    .and(not(named("toString")))
                    .and(not(named("hashCode")))
                    .and(not(named("equals")))
                    .and(not(named("clone")))
                    .and(not(named("finalize")))
                    .and(not(named("getClass")))
                    .and(not(named("builder")))
                    .and(not(named("build")))
                    .and(not(nameStartsWith("lambda$")))
                    .and(not(nameStartsWith("access$")));  // 내부 접근자 메서드 제외

            return builder.method(methodMatcher)
                .intercept(MethodDelegation.to(TraceFlowInterceptor.class));
        }
    }
}