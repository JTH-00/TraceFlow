// ========== Global Variables ==========
let selectedSession = null;
let loadedSessions = new Map();
let autoRefresh = false;
let autoRefreshTimer = null;
let currentData = null;
let filters = {
    showAccessors: false,
    mergeDuplicates: false
};
let zoomBehavior = null;
let currentZoom = 1;
let modalOpen = false;

// ========== Modal Functions ==========
function openModal(nodeData) {
    modalOpen = true;
    const modal = document.getElementById('modal');
    const modalBody = document.getElementById('modal-body');

    if (zoomBehavior) {
        d3.select("svg").on('.zoom', null);
        d3.select("svg").classed('modal-open', true);
    }

    let html = `
        <div class="modal-row">
            <div class="modal-label">Class Name</div>
            <div class="modal-value">${nodeData.className}</div>
        </div>

        <div class="modal-row">
            <div class="modal-label">Method Name</div>
            <div class="modal-value">${nodeData.methodName}</div>
        </div>
    `;

    // Show additional info for merged methods
    if (nodeData.mergedCount && nodeData.mergedCount > 1) {
        html += `
            <div class="modal-row">
                <div class="modal-label">Merged Count</div>
                <div class="modal-value">${nodeData.mergedCount} calls</div>
            </div>

            <div class="modal-row">
                <div class="modal-label">Total Duration</div>
                <div class="modal-value">${nodeData.totalDuration}ms (avg: ${Math.round(nodeData.totalDuration / nodeData.mergedCount)}ms)</div>
            </div>
        `;
    }

    html += `
        <div class="modal-row">
            <div class="modal-label">Parameters</div>
            <div>
                ${nodeData.parameterTypes && nodeData.parameterTypes.length > 0
                    ? `<ul class="param-list">
                        ${nodeData.parameterTypes.map(type => `<li class="param-item">${type}</li>`).join('')}
                       </ul>`
                    : '<div class="no-param">No parameters</div>'
                }
            </div>
        </div>

        <div class="modal-row">
            <div class="modal-label">Return Type</div>
            <div class="modal-value">${nodeData.returnType}</div>
        </div>

        <div class="modal-row">
            <div class="modal-label">Duration</div>
            <div class="modal-value">${nodeData.duration}ms</div>
        </div>

        <div class="modal-row">
            <div class="modal-label">Start Time</div>
            <div class="modal-value">${new Date(nodeData.startTime).toLocaleString()}</div>
        </div>

        <div class="modal-row">
            <div class="modal-label">Async</div>
            <div class="modal-value">${nodeData.async ? 'Yes' : 'No'}</div>
        </div>
    `;

    if (nodeData.isError) {
        html += `
            <div class="error-section">
                <div class="modal-row">
                    <div class="modal-label">⚠️ Error Occurred</div>
                    <div></div>
                </div>

                <div class="modal-row">
                    <div class="modal-label">Error Type</div>
                    <div class="modal-value">${nodeData.errorType || 'Unknown'}</div>
                </div>

                <div class="modal-row">
                    <div class="modal-label">Error Message</div>
                    <div class="modal-value">${nodeData.errorMessage || 'No message'}</div>
                </div>

                ${nodeData.stackTrace ? `
                    <div class="modal-row">
                        <div class="modal-label">Stack Trace</div>
                        <div class="modal-value stack-trace">${nodeData.stackTrace}</div>
                    </div>
                ` : ''}
            </div>
        `;
    }

    modalBody.innerHTML = html;
    modal.classList.add('active');
}

function closeModal() {
    modalOpen = false;
    const modal = document.getElementById('modal');
    modal.classList.remove('active');

    if (zoomBehavior) {
        d3.select("svg").call(zoomBehavior);
        d3.select("svg").classed('modal-open', false);
    }
}

function closeModalOnOverlay(event) {
    if (event.target.id === 'modal') {
        closeModal();
    }
}

// ========== Zoom Functions ==========
function zoomIn() {
    if (!zoomBehavior || modalOpen) return;
    const svg = d3.select("svg");
    svg.transition().duration(300).call(zoomBehavior.scaleBy, 1.3);
}

function zoomOut() {
    if (!zoomBehavior || modalOpen) return;
    const svg = d3.select("svg");
    svg.transition().duration(300).call(zoomBehavior.scaleBy, 0.7);
}

function zoomReset() {
    if (!zoomBehavior || modalOpen) return;
    const svg = d3.select("svg");
    const margin = { top: 50, right: 50, bottom: 50, left: 150 };
    svg.transition().duration(500).call(
        zoomBehavior.transform,
        d3.zoomIdentity.translate(margin.left, margin.top)
    );
}

// ========== Session Functions ==========
async function checkNewSessions() {
    updateStatus("Checking for new sessions...");

    try {
        const res = await fetch("/logs?action=new-sessions");
        const data = await res.json();

        if (data.hasNew) {
            const newSessionsArray = data.newSessions;

            for (const sessionId of newSessionsArray) {
                await loadSession(sessionId, true);
            }

            renderSessionList();

            if (newSessionsArray.length > 0) {
                const latestSession = newSessionsArray[0];
                selectSession(latestSession);
            }
        } else {
            updateStatus("No new sessions");
        }
    } catch (error) {
        console.error("Error checking sessions:", error);
        updateStatus("Error occurred");
    }
}

async function loadSession(sessionId, isNew = false) {
    if (loadedSessions.has(sessionId)) {
        return loadedSessions.get(sessionId);
    }

    try {
        const res = await fetch(`/logs?sessionId=${sessionId}`);
        const data = await res.json();

        // Find entry point method name
        const entryPoint = data.find(entry => entry.methodType === 'ENTRY_POINT');
        const entryMethodName = entryPoint
            ? `${entryPoint.className.split('.').pop()}.${entryPoint.methodName}`
            : 'Unknown';

        loadedSessions.set(sessionId, {
            id: sessionId,
            data: data,
            isNew: isNew,
            timestamp: new Date().toLocaleTimeString(),
            entryMethodName: entryMethodName
        });

        return data;
    } catch (error) {
        console.error(`Error loading session ${sessionId}:`, error);
        return null;
    }
}

function renderSessionList() {
    const container = document.getElementById("session-list");
    container.innerHTML = "";

    if (loadedSessions.size === 0) {
        container.innerHTML = '<div class="empty-state">No sessions</div>';
        return;
    }

    const sessions = Array.from(loadedSessions.values()).reverse();

    sessions.forEach(session => {
        const div = document.createElement("div");
        div.className = "session-item";

        if (session.id === selectedSession) {
            div.classList.add("active");
        }

        if (session.isNew) {
            div.classList.add("new");
        }

        div.innerHTML = `
            <div>${session.entryMethodName}</div>
            <div class="session-info">
                ${session.timestamp} | ${session.data.length} calls
            </div>
        `;

        div.onclick = () => selectSession(session.id);
        container.appendChild(div);
    });

    document.getElementById("sessionCount").textContent = loadedSessions.size;
}

function selectSession(sessionId) {
    selectedSession = sessionId;
    const session = loadedSessions.get(sessionId);

    if (session) {
        session.isNew = false;
        currentData = session.data;

        renderSessionList();
        updateFilterCounts(currentData);
        applyFilters();

        updateStatus(`Displaying session: ${session.entryMethodName}`);
    }
}

// ========== Filter Functions ==========
function updateFilterCounts(data) {
    if (!data || data.length === 0) return;

    let accessorCount = 0;
    let duplicateCount = 0;

    // Count Getter/Setter in original data
    data.forEach(entry => {
        const type = entry.methodType || 'BUSINESS';
        if (type === 'GETTER' || type === 'SETTER') {
            accessorCount++;
        }
    });

    // Count duplicates - merge recursively first, then count how many were merged
    const merged = mergeDuplicatesRecursive(data);
    duplicateCount = data.length - merged.length;

    document.getElementById('count-accessor').textContent = accessorCount;
    document.getElementById('count-duplicates').textContent = duplicateCount;
}

function applyFilters() {
    if (!currentData) return;

    filters.showAccessors = document.getElementById('filter-accessor').checked;
    filters.mergeDuplicates = document.getElementById('merge-duplicates').checked;

    let filteredData = [...currentData];

    // Step 1: Apply duplicate merge filter FIRST (recursive)
    if (filters.mergeDuplicates) {
        filteredData = mergeDuplicatesRecursive(filteredData);
    }

    // Step 2: Apply Getter/Setter filter AFTER merging
    filteredData = filteredData.filter(entry => {
        const type = entry.methodType || 'BUSINESS';

        if (type === 'GETTER' || type === 'SETTER') {
            return filters.showAccessors;
        }

        return true;
    });

    document.getElementById('filter-stats').textContent =
        `Showing: ${filteredData.length} / Total: ${currentData.length}`;

    renderGraph(buildTree(filteredData));
}

// Merge duplicate methods recursively at all levels
function mergeDuplicatesRecursive(entries) {
    if (!entries || entries.length === 0) return [];

    // Build tree first
    const tree = buildTreeForMerge(entries);

    // Recursively merge tree
    const mergedTree = mergeTreeRecursively(tree);

    // Flatten back to entries
    return flattenTree(mergedTree);
}

// Build tree structure for merging
function buildTreeForMerge(entries) {
    const nodeMap = {};
    const roots = [];

    entries.forEach(entry => {
        nodeMap[entry.id] = { ...entry, children: [] };
    });

    entries.forEach(entry => {
        if (entry.parentId && nodeMap[entry.parentId]) {
            nodeMap[entry.parentId].children.push(nodeMap[entry.id]);
        } else {
            roots.push(nodeMap[entry.id]);
        }
    });

    return roots;
}

// Recursively merge duplicate methods in tree
function mergeTreeRecursively(nodes) {
    if (!nodes || nodes.length === 0) return [];

    const methodMap = {};

    nodes.forEach(node => {
        const key = `${node.className}.${node.methodName}`;

        if (!methodMap[key]) {
            // First occurrence
            methodMap[key] = {
                ...node,
                originalId: node.id,
                mergedIds: [node.id],
                mergedCount: 1,
                totalDuration: node.duration,
                children: node.children || []
            };
        } else {
            // Duplicate found - merge
            methodMap[key].mergedIds.push(node.id);
            methodMap[key].mergedCount++;
            methodMap[key].totalDuration += node.duration;

            // Merge children
            methodMap[key].children = methodMap[key].children.concat(node.children || []);
        }
    });

    // Recursively merge children for each merged node
    const merged = Object.values(methodMap).map(entry => {
        if (entry.children && entry.children.length > 0) {
            entry.children = mergeTreeRecursively(entry.children);
        }
        // Update duration to total
        entry.duration = entry.totalDuration;
        return entry;
    });

    return merged;
}

// Flatten tree back to entry list
function flattenTree(nodes, parentId = null) {
    if (!nodes || nodes.length === 0) return [];

    const result = [];

    nodes.forEach(node => {
        const entry = {
            ...node,
            parentId: parentId
        };

        // Remove children from entry (will be added separately)
        const children = entry.children;
        delete entry.children;

        result.push(entry);

        if (children && children.length > 0) {
            const childEntries = flattenTree(children, node.id);
            result.push(...childEntries);
        }
    });

    return result;
}

// ========== Graph Functions ==========
function buildTree(entries) {
    if (!entries || entries.length === 0) return null;

    const nodeMap = {};
    const roots = [];

    entries.forEach(entry => {
        nodeMap[entry.id] = { ...entry, children: [] };

        // Map all IDs for merged items
        if (entry.mergedIds && entry.mergedIds.length > 1) {
            entry.mergedIds.forEach(id => {
                nodeMap[id] = nodeMap[entry.id];
            });
        }
    });

    entries.forEach(entry => {
        if (entry.parentId && nodeMap[entry.parentId]) {
            const parent = nodeMap[entry.parentId];
            // Prevent duplicate additions
            if (!parent.children.some(child => child.id === entry.id)) {
                parent.children.push(nodeMap[entry.id]);
            }
        } else {
            if (!roots.some(root => root.id === entry.id)) {
                roots.push(nodeMap[entry.id]);
            }
        }
    });

    return roots.length > 0 ? roots[0] : null;
}

function renderGraph(rootNode) {
    const container = document.getElementById('graph-container');
    const svg = d3.select("#graph-container svg");
    svg.selectAll("*").remove();

    if (!rootNode) {
        svg.append("text")
            .attr("x", 50)
            .attr("y", 50)
            .text("No data available")
            .style("fill", "#6c757d");
        return;
    }

    const width = container.clientWidth;
    const height = container.clientHeight;
    const margin = { top: 50, right: 50, bottom: 50, left: 150 };

    zoomBehavior = d3.zoom()
        .scaleExtent([0.1, 10])
        .filter(function(event) {
            return !modalOpen;
        })
        .on("zoom", (event) => {
            if (!modalOpen) {
                svg.select("g").attr("transform", event.transform)
                currentZoom = event.transform.k;
                document.getElementById('zoom-level').textContent =
                    `${Math.round(currentZoom * 100)}%`;
            }
        });

    const initialTransform = d3.zoomIdentity.translate(margin.left, margin.top);
    svg.call(zoomBehavior)
       .call(zoomBehavior.transform, initialTransform);

    const treeLayout = d3.tree()
        .nodeSize([80, 200]);

    const root = d3.hierarchy(rootNode);
    treeLayout(root);

    const g = svg.append("g")
        .attr("transform", `translate(${margin.left},${margin.top})`);

    g.selectAll(".link")
        .data(root.links())
        .enter().append("path")
        .attr("class", "link")
        .attr("d", d3.linkHorizontal().x(d => d.y).y(d => d.x));

    const node = g.selectAll(".node")
        .data(root.descendants())
        .enter().append("g")
        .attr("class", d => {
            let classes = "node";
            const type = d.data.methodType || 'BUSINESS';

            if (type === 'ERROR'|| d.data.isError) {
                classes += " error";
            }

            classes += " " + type.toLowerCase().replace('_', '-');

            // Mark merged nodes
            if (d.data.mergedCount && d.data.mergedCount > 1) {
                classes += " merged";
            }

            return classes;
        })
        .attr("transform", d => `translate(${d.y},${d.x})`)
        .on("click", function(event, d) {
            event.stopPropagation();
            openModal(d.data);
        });

    node.append("circle").attr("r", 5);

    node.append("text")
        .attr("dy", -10)
        .attr("text-anchor", "middle")
        .text(d => {
            const className = d.data.className ?
                d.data.className.split('.').pop() : 'Unknown';
            const methodName = d.data.methodName;
            const count = d.data.mergedCount > 1 ? ` (×${d.data.mergedCount})` : '';
            return `${className}.${methodName}${count}`;
        });

    node.append("text")
        .attr("dy", 20)
        .attr("text-anchor", "middle")
        .style("font-size", "9px")
        .style("fill", "#6c757d")
        .text(d => `${d.data.duration}ms`);

    document.getElementById('zoom-level').textContent = '100%';
}

// ========== Utility Functions ==========
function updateStatus(message) {
    document.getElementById("status").textContent =
        `${new Date().toLocaleTimeString()} - ${message}`;
}

function toggleAutoRefresh() {
    autoRefresh = !autoRefresh;
    const btn = document.getElementById("autoBtn");

    if (autoRefresh) {
        btn.textContent = "Auto: ON";
        btn.style.background = "#28a745";
        autoRefreshTimer = setInterval(checkNewSessions, 5000);
    } else {
        btn.textContent = "Auto: OFF";
        btn.style.background = "#6c757d";
        clearInterval(autoRefreshTimer);
    }
}

// ========== Event Listeners ==========
let resizeTimer;
window.addEventListener('resize', function() {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function() {
        if (currentData) {
            applyFilters();
        }
    }, 250);
});

document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape' && modalOpen) {
        closeModal();
    }
});

// ========== Initialization ==========
async function init() {
    updateStatus("Initializing...");
    await checkNewSessions();
    updateStatus("Ready");
}

init();