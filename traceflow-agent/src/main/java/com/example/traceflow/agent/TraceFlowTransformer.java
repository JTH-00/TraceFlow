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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class TraceFlowTransformer {
    private static final int DEFAULT_PORT = 8081;
    private static final String KEY_PORT = "port";
    private static final String KEY_PACKAGE = "package";
    private static final String JETTY_SERVER_CLASS = "org.eclipse.jetty.server.Server";
    private static final String TRACEFLOW_WEB_SERVER_CLASS = "com.example.traceflow.server.TraceFlowWebServer";

    private static final List<String> IGNORED_PACKAGE_PREFIXES = List.of(
        "net.bytebuddy", "java.", "javax.", "jakarta.",
        "sun.", "jdk.", "org.springframework",
        "org.hibernate", "com.mysql", "com.zaxxer",
        "com.example.traceflow"
    );

    private static final List<String> EXCLUDED_METHOD_NAMES = List.of(
        "toString", "hashCode", "equals", "clone",
        "finalize", "getClass", "builder", "build"
    );

    private static final String LAMBDA_PREFIX = "lambda$";
    private static final String ACCESSOR_PREFIX = "access$";

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[TraceFlow Agent] Starting instrumentation...");

        Map<String, String> args = parseAgentArgs(agentArgs);
        int port = args.containsKey(KEY_PORT) ? parsePort(args.get(KEY_PORT)) : DEFAULT_PORT;
        String targetPackage = args.get(KEY_PACKAGE);

        startWebServer(port);
        installEntryPointTransformer(inst);
        installUniversalTransformer(inst, targetPackage);

        System.out.println("[TraceFlow Agent] Instrumentation installed successfully");
    }

    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> map = new HashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) return map;
        for (String part : agentArgs.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }

    private static int parsePort(String portStr) {
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            System.err.println("[TraceFlow] Invalid port: " + portStr + ", using default " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private static void startWebServer(int port) {
        // Jetty 서버 시작 (별도 스레드)
        final int finalPort = port;
        Thread serverThread = new Thread(() -> {
            try {
                // Jetty 클래스 확인
                Class.forName(JETTY_SERVER_CLASS);

                // TraceFlowWebServer 시작
                Class<?> webServerClass = Class.forName(TRACEFLOW_WEB_SERVER_CLASS);
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
        ElementMatcher.Junction<TypeDescription> matcher = none();
        for (String prefix : IGNORED_PACKAGE_PREFIXES) {
            matcher = matcher.or(nameStartsWith(prefix));
        }
        return matcher;
    }

    // @TraceFlow 진입점 변환기
    static class EntryPointTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                TypeDescription typeDescription,
                                                ClassLoader classLoader,
                                                JavaModule javaModule,
                                                ProtectionDomain protectionDomain) {

            boolean classHasTraceFlow = typeDescription.getDeclaredAnnotations().isAnnotationPresent(TraceFlow.class);

            ElementMatcher<MethodDescription> methodMatcher;

            if (classHasTraceFlow) {
                // 클래스 레벨 @TraceFlow: 모든 public 메서드가 진입점
                methodMatcher = isPublic()
                    .and(not(isDeclaredBy(Object.class)))
                    .and(not(isConstructor()))
                    .and(not(isStatic()))
                    .and(not(isSynthetic()))
                    .and(not(isAbstract()));
            } else {
                // 메서드 레벨 @TraceFlow: 해당 메서드만 진입점
                methodMatcher = isAnnotatedWith(TraceFlow.class)
                    .and(not(isDeclaredBy(Object.class)))
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
            ElementMatcher.Junction<MethodDescription> methodMatcher =
                not(isConstructor())
                    .and(not(isStatic()))
                    .and(not(isSynthetic()))
                    .and(not(isBridge()))
                    .and(not(isNative()))
                    .and(not(isAbstract()))
                    .and(not(isDeclaredBy(nameContains("$"))));

            for (String name : EXCLUDED_METHOD_NAMES) {
                methodMatcher = methodMatcher.and(not(named(name)));
            }

            methodMatcher = methodMatcher
                .and(not(nameStartsWith(LAMBDA_PREFIX)))
                .and(not(nameStartsWith(ACCESSOR_PREFIX)));

            return builder.method(methodMatcher)
                .intercept(MethodDelegation.to(TraceFlowInterceptor.class));
        }
    }
}