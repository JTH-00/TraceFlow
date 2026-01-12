package io.github.jth00.traceflow;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Gradle plugin for TraceFlow agent injection
 * Automatically injects the Java agent into JavaExec tasks
 */
public class TraceFlowPlugin implements Plugin<Project> {

    private static final String EXTENSION_NAME = "traceFlow";
    private static final String AGENT_CONFIGURATION = "traceflowAgent";
    private static final String AGENT_GROUP = "io.github.jth-00";
    private static final String AGENT_ARTIFACT = "traceflow-agent";

    @Override
    public void apply(Project project) {
        project.getLogger().lifecycle("[TraceFlow] Plugin Applied to " + project.getName());

        // Create extension
        TraceFlowExtension ext = project.getExtensions()
            .create(EXTENSION_NAME, TraceFlowExtension.class);

        // Create configuration for agent dependency
        Configuration agentConfig = project.getConfigurations().maybeCreate(AGENT_CONFIGURATION);
        agentConfig.setDescription("TraceFlow agent JAR for bytecode instrumentation");
        agentConfig.setTransitive(false); // Only get the agent JAR itself

        project.afterEvaluate(p -> {
            if (!ext.isAutoInject()) {
                project.getLogger().lifecycle("[TraceFlow] Auto-injection is disabled");
                return;
            }

            if (ext.getPackagePath() == null || ext.getPackagePath().isEmpty()) {
                throw new GradleException("[TraceFlow] packagePath not specified in traceFlow configuration");
            }

            if (ext.getPackagePath().equals("com") || ext.getPackagePath().equals("org")) {
                project.getLogger().warn("[TraceFlow] Package path '{}' is too broad and may cause proxy conflicts. " +
                    "Please specify a more specific package path (e.g., 'com.example.myapp')", ext.getPackagePath());
            }

            // Resolve agent JAR from Maven Central
            File agentJar = resolveAgentFromMavenCentral(project, agentConfig);

            // Inject agent into JavaExec tasks
            project.getTasks().withType(JavaExec.class).configureEach(task -> {
                // Support both 'run' and 'bootRun' tasks
                if (task.getName().equals("run") ||
                    task.getName().equals("bootRun") ||
                    task.getName().contains("Run")) {
                    configureJavaAgent(project, task, agentJar, ext);
                }
            });
        });
    }

    /**
     * Resolve agent JAR from Maven Central
     * Uses the same version as the plugin
     */
    private File resolveAgentFromMavenCentral(Project project, Configuration agentConfig) {
        try {
            // Get plugin version
            String version = getPluginVersion(project);
            String dependency = String.format("%s:%s:%s", AGENT_GROUP, AGENT_ARTIFACT, version);

            // Add dependency
            project.getDependencies().add(AGENT_CONFIGURATION, dependency);

            project.getLogger().lifecycle("[TraceFlow] Resolving agent from Maven Central: " + dependency);

            // Resolve and get JAR file
            Set<File> files = agentConfig.resolve();

            if (files.isEmpty()) {
                throw new GradleException(
                    String.format("[TraceFlow] Failed to resolve agent JAR from Maven Central: %s", dependency));
            }

            File agentJar = files.iterator().next();
            project.getLogger().lifecycle("[TraceFlow] Agent resolved: " + agentJar.getName());

            return agentJar;

        } catch (Exception e) {
            throw new GradleException(
                "[TraceFlow] Failed to resolve agent JAR from Maven Central. " +
                    "Please ensure you have internet connection and Maven Central is accessible.", e);
        }
    }

    /**
     * Get plugin version to match with agent version
     * The agent version always matches the plugin version
     */
    private String getPluginVersion(Project project) {
        // Try to read version from plugin metadata
        String version = getClass().getPackage().getImplementationVersion();

        if (version != null && !version.isEmpty() && !version.equals("unspecified")) {
            project.getLogger().debug("[TraceFlow] Using version from manifest: " + version);
            return version;
        }

        throw new GradleException(
            "[TraceFlow] Could not determine plugin version. " +
                "This indicates the plugin was not properly built. " +
                "Please report this issue at https://github.com/jth-00/traceflow/issues"
        );
    }

    /**
     * Configure Java agent for a specific task
     * @param project Gradle project
     * @param task JavaExec task to configure
     * @param agentJar Agent JAR file
     * @param ext TraceFlow extension configuration
     */
    private void configureJavaAgent(Project project, JavaExec task, File agentJar, TraceFlowExtension ext) {
        task.doFirst(t -> {
            if (!agentJar.exists()) {
                throw new GradleException(
                    "[TraceFlow] Agent JAR not found: " + agentJar.getAbsolutePath() +
                        ". This should not happen - please report this issue.");
            }

            JavaExec execTask = (JavaExec) t;
            String agentArg = "-javaagent:" + agentJar.getAbsolutePath();

            // Configure port and package
            String options = String.format("port=%d,package=%s",
                ext.getWebServerPort(),
                ext.getPackagePath());
            agentArg += "=" + options;

            List<String> newJvmArgs = new ArrayList<>(Objects.requireNonNull(execTask.getJvmArgs()));

            // Prevent duplicate additions
            boolean alreadyAdded = newJvmArgs.stream()
                .anyMatch(arg -> arg.startsWith("-javaagent:") && arg.contains("traceflow-agent"));

            if (!alreadyAdded) {
                newJvmArgs.add(agentArg);
                project.getLogger().lifecycle("[TraceFlow] Injected agent: " + agentArg);
            } else {
                project.getLogger().debug("[TraceFlow] Agent already injected, skipping");
            }

            execTask.setJvmArgs(newJvmArgs);
        });
    }
}
