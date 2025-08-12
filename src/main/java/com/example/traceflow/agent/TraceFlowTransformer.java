package com.example.traceflow.agent;

import com.example.traceflow.anotation.TraceFlow;
import com.example.traceflow.interceptor.TraceFlowInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class TraceFlowTransformer implements AgentBuilder.Transformer {

    @Override
    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                            TypeDescription typeDescription,
                                            ClassLoader classLoader,
                                            JavaModule javaModule,
                                            ProtectionDomain protectionDomain) {
        return builder
                .method(
                        ElementMatchers.isAnnotatedWith(TraceFlow.class)
                                .or(ElementMatchers.isDeclaredBy(ElementMatchers.isAnnotatedWith(TraceFlow.class)))
                )
                .intercept(net.bytebuddy.implementation.MethodDelegation.to(TraceFlowInterceptor.class));
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        new AgentBuilder.Default()
                .type(ElementMatchers.any())
                .transform(new TraceFlowTransformer())
                .installOn(inst);
    }
}