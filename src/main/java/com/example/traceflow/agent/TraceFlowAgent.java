package com.example.traceflow.agent;

import com.example.traceflow.interceptor.TraceFlowInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;

public class TraceFlowAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[TraceFlowAgent] Starting instrumentation...");

        new AgentBuilder.Default()
                .type(ElementMatchers.any())
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.method(ElementMatchers.any())
                                .intercept(MethodDelegation.to(TraceFlowInterceptor.class))
                )
                .installOn(inst);
    }
}