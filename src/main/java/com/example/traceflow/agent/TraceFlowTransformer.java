package com.example.traceflow.agent;

import com.example.traceflow.anotation.TraceFlow;
import com.example.traceflow.interceptor.TraceFlowInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.springframework.stereotype.Component;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class TraceFlowTransformer implements AgentBuilder.Transformer {

    public static void premain(String agentArgs, Instrumentation inst) {
        new AgentBuilder.Default()
            .type(
                ElementMatchers.isAnnotatedWith(TraceFlow.class)
                    .and(ElementMatchers.isAnnotatedWith(Component.class))
                    .or(
                        ElementMatchers.declaresMethod(ElementMatchers.isAnnotatedWith(TraceFlow.class)
                            .and(ElementMatchers.isPublic()))
                    )
            )
            .transform(new TraceFlowTransformer())
            .installOn(inst);
    }

    @Override
    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                            TypeDescription typeDescription,
                                            ClassLoader classLoader,
                                            JavaModule javaModule,
                                            ProtectionDomain protectionDomain) {
        return builder
            .method(
                ElementMatchers.isPublic().and(
                    ElementMatchers.isAnnotatedWith(TraceFlow.class)
                        .or(
                            ElementMatchers.isDeclaredBy(
                                ElementMatchers.isAnnotatedWith(TraceFlow.class)
                                    .and(ElementMatchers.isAnnotatedWith(Component.class))
                            )
                        )
                )
            )
            .intercept(net.bytebuddy.implementation.MethodDelegation.to(TraceFlowInterceptor.class));
    }
}