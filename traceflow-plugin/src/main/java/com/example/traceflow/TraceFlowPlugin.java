package com.example.traceflow;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TraceFlowPlugin implements Plugin<Project> {

    private static final String EXTENSION_NAME = "traceFlow";
    @Override
    public void apply(Project project) {
        project.getLogger().lifecycle("[TraceFlow] Plugin Applied to " + project.getName());

        // Extension 생성
        TraceFlowExtension ext = project.getExtensions()
            .create(EXTENSION_NAME, TraceFlowExtension.class);

        project.afterEvaluate(p -> {
            if (!ext.isAutoInject()) {
                project.getLogger().lifecycle("[TraceFlow] Auto-injection is disabled");
                return;
            }

            if (ext.getAgentPath() == null || ext.getAgentPath().isEmpty()) {
                throw new GradleException("[TraceFlow] agentPath not specified in traceFlow configuration");
            }

            if (ext.getPackagePath() == null || ext.getPackagePath().isEmpty()) {
                throw new GradleException("[TraceFlow] packagePath not specified in traceFlow configuration");
            }

            if (ext.getPackagePath().equals("com") || ext.getPackagePath().equals("org")) {
                project.getLogger().warn("[TraceFlow] Package path '{}' is too broad and may cause proxy conflicts. " +
                    "Please specify a more specific package path (e.g., 'com.example.myapp')", ext.getPackagePath());
            }

            File agentJar = new File(ext.getAgentPath());

            // JavaExec 태스크에 agent 주입
            project.getTasks().withType(JavaExec.class).configureEach(task -> {
                // bootRun 추가
                if (task.getName().equals("run") ||
                    task.getName().equals("bootRun") ||  // 추가
                    task.getName().contains("Run")) {
                    configureJavaAgent(project, task, agentJar, ext);
                }
            });
        });
    }

    private void configureJavaAgent(Project project, JavaExec task, File agentJar, TraceFlowExtension ext) {
        task.doFirst(t -> {
            File actualAgentJar = agentJar;
            if (ext.getAgentPath() != null && !ext.getAgentPath().isEmpty()) {
                actualAgentJar = new File(ext.getAgentPath());
            }

            if (!actualAgentJar.exists()) {
                project.getLogger().error("[TraceFlow] Agent JAR not found: " + actualAgentJar.getAbsolutePath());
                project.getLogger().error("[TraceFlow] Please specify agentPath in traceFlow configuration");
                return;
            }

            JavaExec execTask = (JavaExec) t;
            String agentArg = "-javaagent:" + actualAgentJar.getAbsolutePath();

            // 포트와 패키지 설정
            String options = String.format("port=%d,package=%s",
                ext.getWebServerPort(),
                ext.getPackagePath());
            agentArg += "=" + options;

            List<String> newJvmArgs = new ArrayList<>(Objects.requireNonNull(execTask.getJvmArgs()));

            // 중복 추가 방지
            if (!newJvmArgs.contains(agentArg)) {
                newJvmArgs.add(agentArg);
                project.getLogger().lifecycle("[TraceFlow] Injected agent: " + agentArg);
            }

            execTask.setJvmArgs(newJvmArgs);
        });
    }
}
