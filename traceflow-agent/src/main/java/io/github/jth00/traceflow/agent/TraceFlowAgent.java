package io.github.jth00.traceflow.agent;

import io.github.jth00.traceflow.anotations.TraceFlow;
import io.github.jth00.traceflow.interceptor.EntryPointInterceptor;
import io.github.jth00.traceflow.interceptor.TraceFlowInterceptor;
import io.github.jth00.traceflow.server.TraceFlowWebServer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * ByteBuddy agent for instrumenting Java methods with TraceFlow
 */
public class TraceFlowAgent {
    private static final int DEFAULT_PORT = 8081;
    private static final String KEY_PORT = "port";
    private static final String KEY_PACKAGE = "package";
    private static final String JETTY_SERVER_CLASS = "org.eclipse.jetty.server.Server";

    // Packages to exclude from instrumentation
    private static final List<String> IGNORED_PACKAGE_PREFIXES = List.of(
        "net.bytebuddy", "java.", "javax.", "jakarta.",
        "sun.", "jdk.", "org.springframework",
        "org.hibernate", "com.mysql", "com.zaxxer",
        "io/github/jth00/traceflow"
    );

    // Common methods to exclude from tracing
    private static final List<String> EXCLUDED_METHOD_NAMES = List.of(
        "toString", "hashCode", "equals", "clone",
        "finalize", "getClass", "builder", "build"
    );

    private static final String LAMBDA_PREFIX = "lambda$";
    private static final String ACCESSOR_PREFIX = "access$";

    /**
     * Agent entry point called before main method
     * @param agentArgs Agent arguments in format: ex)port=8081,package=com.example
     * @param inst Instrumentation instance provided by JVM
     */
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

    /**
     * Parse agent arguments from command line
     * @param agentArgs Comma-separated key=value pairs
     * @return Map of parsed arguments
     */
    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> map = new HashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) return map;
        for (String part : agentArgs.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }

    /**
     * Parse port number from string
     * @param portStr Port number as string
     * @return Parsed port number or default if invalid
     */
    private static int parsePort(String portStr) {
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            System.err.println("[TraceFlow] Invalid port: " + portStr + ", using default " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    /**
     * Start Jetty web server in a separate daemon thread
     * @param port Port number for web server
     */
    private static void startWebServer(int port) {
        final int finalPort = port;
        Thread serverThread = new Thread(() -> {
            try {
                // Check if Jetty is available
                Class.forName(JETTY_SERVER_CLASS);

                // Start TraceFlowWebServer
                TraceFlowWebServer.start(finalPort);

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

    /**
     * Install transformer for @TraceFlow entry points
     * Instruments methods annotated with @TraceFlow
     */
    private static void installEntryPointTransformer(Instrumentation inst) {
        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .ignore(getIgnoreMatcher())
            .type(
                // Classes or methods with @TraceFlow annotation
                isAnnotatedWith(TraceFlow.class)
                    .or(declaresMethod(isAnnotatedWith(TraceFlow.class)))
            )
            .transform(new EntryPointTransformer())
            .installOn(inst);
    }

    /**
     * Install transformer for all application methods
     * Traces all methods in the specified package
     * @param inst Instrumentation instance
     * @param targetPackage Package path to instrument (e.g., "com.example.myapp")
     */
    private static void installUniversalTransformer(Instrumentation inst, String targetPackage) {
        if (targetPackage == null || targetPackage.isEmpty()) {
            throw new IllegalArgumentException("[TraceFlow Agent] Package path is required. " +
                "Please specify package path in traceFlow configuration.");
        }

        new AgentBuilder.Default()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .ignore(getIgnoreMatcher())
            .type(
                // User application package
                nameStartsWith(targetPackage)
                    .and(not(nameContains("$$"))) // Exclude proxies
                    .and(not(nameContains("CGLIB"))) // Exclude CGLIB
            )
            .transform(new UniversalMethodTransformer())
            .installOn(inst);
    }

    /**
     * Create matcher for packages to ignore
     * @return ElementMatcher for ignored packages
     */
    private static ElementMatcher<TypeDescription> getIgnoreMatcher() {
        ElementMatcher.Junction<TypeDescription> matcher = none();
        for (String prefix : IGNORED_PACKAGE_PREFIXES) {
            matcher = matcher.or(nameStartsWith(prefix));
        }
        return matcher;
    }

    /**
     * Transformer for @TraceFlow entry point methods
     */
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
                // Class-level @TraceFlow: all public methods are entry points
                methodMatcher = isPublic()
                    .and(not(isDeclaredBy(Object.class)))
                    .and(not(isConstructor()))
                    .and(not(isStatic()))
                    .and(not(isSynthetic()))
                    .and(not(isAbstract()));
            } else {
                // Method-level @TraceFlow: only annotated methods
                methodMatcher = isAnnotatedWith(TraceFlow.class)
                    .and(not(isDeclaredBy(Object.class)))
                    .and(isPublic());
            }

            return builder.method(methodMatcher)
                .intercept(MethodDelegation.to(EntryPointInterceptor.class));
        }
    }

    /**
     * Transformer for all methods (active only during tracing)
     */
    static class UniversalMethodTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                TypeDescription typeDescription,
                                                ClassLoader classLoader,
                                                JavaModule javaModule,
                                                ProtectionDomain protectionDomain) {
            // Skip auxiliary classes
            if (typeDescription.getName().contains("$auxiliary$") ||
                typeDescription.getName().contains("$$")) {
                return builder;
            }

            //4 Match all methods (including private, protected)
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