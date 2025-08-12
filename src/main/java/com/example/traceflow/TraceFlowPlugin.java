package com.example.traceflow;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class TraceFlowPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getLogger().lifecycle("TraceFlow Plugin Applied");
    }
}