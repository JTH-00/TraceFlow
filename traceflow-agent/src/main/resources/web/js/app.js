// ========== Global Variables ==========
let selectedSession = null;
let loadedSessions = new Map();
let autoRefresh = false;
let autoRefreshTimer = null;
let currentData = null;
let filters = {
    showAccessors: false
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

    if (nodeData.error) {
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
    svg.transition().duration(500).call(
        zoomBehavior.transform,
        d3.zoomIdentity.translate(150, 50)
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

    data.forEach(entry => {
        const type = entry.methodType || 'BUSINESS';
        if (type === 'GETTER' || type === 'SETTER') {
            accessorCount++;
        }
    });

    document.getElementById('count-accessor').textContent = accessorCount;
}

function applyFilters() {
    if (!currentData) return;

    filters.showAccessors = document.getElementById('filter-accessor').checked;

    const filteredData = currentData.filter(entry => {
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

// ========== Graph Functions ==========
function buildTree(entries) {
    if (!entries || entries.length === 0) return null;

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
                g.attr("transform", event.transform);
                currentZoom = event.transform.k;
                document.getElementById('zoom-level').textContent =
                    `${Math.round(currentZoom * 100)}%`;
            }
        });

    svg.call(zoomBehavior);

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
            if (d.data.error) classes += " error";

            const type = d.data.methodType || 'BUSINESS';
            classes += " " + type.toLowerCase().replace('_', '-');

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
            return `${className}.${d.data.methodName}`;
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