package com.example.traceflow.agent;

import com.example.traceflow.anotation.TraceFlow;
import com.example.traceflow.interceptor.TraceFlowInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class TraceFlowTransformer implements AgentBuilder.Transformer {

    public static void premain(String agentArgs, Instrumentation inst) {
        new AgentBuilder.Default()
                .type(ElementMatchers.isAnnotatedWith(TraceFlow.class)
                        .or(ElementMatchers.isSubTypeOf(Controller.class))
                        .or(ElementMatchers.isSubTypeOf(Service.class))
                        .or(ElementMatchers.isSubTypeOf(Component.class))
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
                        ElementMatchers.isAnnotatedWith(TraceFlow.class)
                                .or(ElementMatchers.isDeclaredBy(ElementMatchers.isAnnotatedWith(TraceFlow.class)))
                )
                .intercept(net.bytebuddy.implementation.MethodDelegation.to(TraceFlowInterceptor.class));
    }
}