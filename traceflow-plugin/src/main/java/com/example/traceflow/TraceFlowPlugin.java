package com.example.traceflow;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.testing.Test;

import java.io.File;

public class TraceFlowPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getLogger().lifecycle("[TraceFlow] Plugin Applied to " + project.getName());

        // Extension 생성
        TraceFlowExtension ext = project.getExtensions()
            .create("traceFlow", TraceFlowExtension.class);

        // 기본값 설정
        ext.setAutoInject(true);
        ext.setWebServerPort(8081);
        ext.setIncludeTests(false);

        project.afterEvaluate(p -> {
            if (!ext.isAutoInject()) {
                project.getLogger().lifecycle("[TraceFlow] Auto-injection is disabled");
                return;
            }

            // JAR 파일 경로 계산
            String agentJarName = project.getName() + "-" + project.getVersion() + ".jar";
            File agentJar = project.getBuildDir().toPath()
                .resolve("libs")
                .resolve(agentJarName)
                .toFile();

            // JavaExec 태스크에 agent 주입
            project.getTasks().withType(JavaExec.class).configureEach(task -> {
                // run 태스크만 처리 (또는 특정 태스크 필터링)
                if (task.getName().equals("run") || task.getName().contains("Run")) {
                    configureJavaAgent(project, task, agentJar, ext);
                }
            });

        });
    }

    private void configureJavaAgent(Project project, JavaExec task, File agentJar, TraceFlowExtension ext) {
        task.dependsOn("jar");

        task.doFirst(t -> {
            if (!agentJar.exists()) {
                project.getLogger().warn("[TraceFlow] Agent JAR not found: " + agentJar.getAbsolutePath());
                return;
            }

            JavaExec execTask = (JavaExec) t;
            String agentArg = "-javaagent:" + agentJar.getAbsolutePath();

            // 웹서버 포트 설정
            if (ext.getWebServerPort() != 8081) {
                agentArg += "=" + ext.getWebServerPort();
            }

            // 중복 추가 방지
            if (!execTask.getJvmArgs().contains(agentArg)) {
                execTask.getJvmArgs().add(agentArg);
                project.getLogger().lifecycle("[TraceFlow] Injected agent into task '" + task.getName() + "': " + agentArg);
            }

            // 추가 JVM 옵션 (디버깅용)
            if (ext.isDebugMode()) {
                execTask.getJvmArgs().add("-Dtraceflow.debug=true");
            }
        });
    }

    static class TraceFlowExtension {
        private boolean autoInject = true;
        private int webServerPort = 8081;
        private boolean includeTests = false;
        private boolean debugMode = false;

        public boolean isAutoInject() { return autoInject; }
        public void setAutoInject(boolean autoInject) { this.autoInject = autoInject; }

        public int getWebServerPort() { return webServerPort; }
        public void setWebServerPort(int port) { this.webServerPort = port; }

        public boolean isIncludeTests() { return includeTests; }
        public void setIncludeTests(boolean includeTests) { this.includeTests = includeTests; }

        public boolean isDebugMode() { return debugMode; }
        public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }
    }
}
