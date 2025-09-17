package com.example.traceflow.tree;

import com.example.traceflow.vo.TraceEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TraceTreeBuilder {
    public static List<TraceTreeNode> buildTree(List<TraceEntry> entries) {
        Map<String, TraceTreeNode> nodeMap = new HashMap<>();
        List<TraceTreeNode> roots = new ArrayList<>();

        // 모든 노드를 생성
        for (TraceEntry e : entries) {
            nodeMap.put(e.getId(), new TraceTreeNode(e));
        }

        // parent-child 연결
        for (TraceEntry e : entries) {
            TraceTreeNode node = nodeMap.get(e.getId());
            if (e.getParentId() != null && nodeMap.containsKey(e.getParentId())) {
                nodeMap.get(e.getParentId()).addChild(node);
            } else {
                roots.add(node);
            }
        }

        return roots;
    }
}