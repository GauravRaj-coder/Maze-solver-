// app.js

// STATE VARIABLES
let rows = 21;
let cols = 21;
let grid = []; // 0 = empty, 1 = wall
let startNode = [1, 1];
let endNode = [19, 19];
let fakeEnds = []; // Coordinates of fake target (trap) checkpoints
let activeTool = 'wall'; // 'wall', 'start', 'end', 'fake'

let isSolving = false;
let isDrawingWalls = false;
let isErasingWalls = false;
let isDraggingStart = false;
let isDraggingEnd = false;

let simulationSpeed = 50; // ms per trace step
let animationTimeoutId = null;
let activeTrace = [];
let currentTraceIndex = 0;
let allPaths = []; // Stores all paths returned by backend

// DOM ELEMENTS
const gridContainer = document.getElementById('grid-container');
const algoSelect = document.getElementById('algo-select');
const patternSelect = document.getElementById('pattern-select');
const sizeSelect = document.getElementById('size-select');
const speedSlider = document.getElementById('speed-slider');
const speedVal = document.getElementById('speed-val');
const loopSlider = document.getElementById('loop-slider');
const loopVal = document.getElementById('loop-val');
const generateBtn = document.getElementById('generate-btn');
const solveBtn = document.getElementById('solve-btn');
const clearPathBtn = document.getElementById('clear-path-btn');
const clearGridBtn = document.getElementById('clear-grid-btn');
const statusText = document.getElementById('status-text');
const statVisited = document.getElementById('stat-visited');
const statSolutions = document.getElementById('stat-solutions');
const statPathLen = document.getElementById('stat-path-len');
const statTime = document.getElementById('stat-time');
const logOutput = document.getElementById('log-output');
const clearLogBtn = document.getElementById('clear-log-btn');
const algoExplainContent = document.getElementById('algo-explain-content');

// Multi-path UI
const multiPathContainer = document.getElementById('multi-path-container');
const pathSelect = document.getElementById('path-select');
const overlayPathsCheck = document.getElementById('overlay-paths-check');

// INSIGHTS CONTENT FOR ALGORITHMS
const algoInsights = {
    backtracking: {
        title: "Backtracking DFS (Recursive)",
        desc: "A depth-first recursive pathfinder. It explores a route as deeply as possible. If it hits a wall, dead end, or a <b>Fake Target trap</b>, it backtracks (retraces its steps) to find an alternate route.",
        bullets: [
            "Uses system call stack (Recursion)",
            "Finds and counts <b>every single valid path</b>",
            "Traces backtracks in amber/orange color",
            "Time Complexity: O(4^(V)) in worst case"
        ]
    },
    bfs: {
        title: "Breadth-First Search (BFS)",
        desc: "An unweighted graph traversal algorithm. It explores the maze layer-by-layer radially, expanding like a ripple in water. It uses a First-In-First-Out (FIFO) queue.",
        bullets: [
            "Guaranteed to find the <b>Shortest Path</b>",
            "Does not backtrack; expands uniformly",
            "Explores a wide area before finding goal",
            "Time Complexity: O(V + E)"
        ]
    },
    dijkstra: {
        title: "Dijkstra's Algorithm",
        desc: "The father of pathfinding. It explores paths in strict order of their cumulative distance. In an unweighted grid, it expands in a uniform circle (identical to BFS).",
        bullets: [
            "Guaranteed <b>Shortest Path</b>",
            "Explores cells strictly by path distance",
            "Foundation for many complex routing algos",
            "Time Complexity: O(V log V + E)"
        ]
    },
    astar: {
        title: "A* Search (Manhattan Distance)",
        desc: "An intelligent heuristic-driven pathfinder. It calculates cost: <code>f(n) = g(n) + h(n)</code>, where g is distance from start, and h is the estimated Manhattan distance to the destination.",
        bullets: [
            "Guaranteed <b>Shortest Path</b> (with admissible h)",
            "Extremely focused pathfinding search space",
            "Saves computation by ignoring wrong directions",
            "Time Complexity: O(E log V)"
        ]
    },
    greedy: {
        title: "Greedy Best-First Search",
        desc: "A heuristic-driven pathfinder that makes decisions solely based on <code>h(n)</code> (Manhattan distance to the goal), completely ignoring the path distance from start <code>g(n)</code>.",
        bullets: [
            "Highly aggressive and extremely fast",
            "<b>Not guaranteed</b> to find the shortest path",
            "Sprints straight to the goal, wrapping around walls",
            "Time Complexity: O(E log V) in worst case"
        ]
    },
    bibfs: {
        title: "Bidirectional BFS",
        desc: "Launches two simultaneous BFS searches: one advancing from the <b>Start node</b> (cyan) and one advancing from the <b>End node</b> (magenta). They meet in the middle, splitting search space in half.",
        bullets: [
            "Guaranteed <b>Shortest Path</b>",
            "Visualizes two intersecting search waves",
            "Much faster than single BFS by reducing search area",
            "Time Complexity: O(2 * V^(d/2))"
        ]
    },
    biastar: {
        title: "Bidirectional A*",
        desc: "Launches two simultaneous A* heuristic searches (one from Start, one from End) directed toward each other. They meet at an intersection point, reducing search area drastically.",
        bullets: [
            "Guaranteed <b>Shortest Path</b>",
            "Highly focused meeting waves",
            "Often requires visiting very few cells",
            "Time Complexity: O(E log V) with dual heuristic heaps"
        ]
    }
};

// INITIALIZATION
window.addEventListener('DOMContentLoaded', () => {
    updateAlgoExplanation();
    resetGridDimensions();
    
    // Event Listeners
    algoSelect.addEventListener('change', () => {
        updateAlgoExplanation();
        clearVisuals();
    });
    
    sizeSelect.addEventListener('change', () => {
        resetGridDimensions();
    });
    
    speedSlider.addEventListener('input', (e) => {
        simulationSpeed = parseInt(e.target.value);
        speedVal.innerText = `${simulationSpeed}ms`;
    });

    loopSlider.addEventListener('input', (e) => {
        loopVal.innerText = `${e.target.value}%`;
    });
    
    generateBtn.addEventListener('click', generateMazeFromBackend);
    solveBtn.addEventListener('click', toggleSolve);
    clearPathBtn.addEventListener('click', clearVisuals);
    clearGridBtn.addEventListener('click', resetGrid);
    clearLogBtn.addEventListener('click', () => {
        logOutput.innerHTML = '';
        addLogLine("system-msg", "Log cleared.");
    });

    // Tool Mode Buttons Hookup
    const toolBtns = document.querySelectorAll('.tool-btn');
    toolBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            toolBtns.forEach(b => b.classList.remove('active'));
            activeTool = e.currentTarget.dataset.tool;
            e.currentTarget.classList.add('active');
            addLogLine("system-msg", `Click tool mode switched to: ${e.currentTarget.innerText.trim()}`);
        });
    });
    
    // Speed Slider initialization
    simulationSpeed = parseInt(speedSlider.value);
    speedVal.innerText = `${simulationSpeed}ms`;

    // Loops Slider initialization
    loopVal.innerText = `${loopSlider.value}%`;
});

// ALGO INSIGHT UPDATE
function updateAlgoExplanation() {
    const algo = algoSelect.value;
    const info = algoInsights[algo];
    
    let html = `<p><strong>${info.title}</strong></p>`;
    html += `<p>${info.desc}</p>`;
    html += `<ul>`;
    info.bullets.forEach(bullet => {
        html += `<li><i class="fa-solid fa-chevron-right text-start" style="font-size:0.6rem; margin-right:4px;"></i> ${bullet}</li>`;
    });
    html += `</ul>`;
    
    algoExplainContent.innerHTML = html;
}

// SETUP GRID DIMENSIONS
function resetGridDimensions() {
    stopSimulation();
    const size = parseInt(sizeSelect.value);
    rows = size;
    cols = size;
    startNode = [1, 1];
    endNode = [size - 2, size - 2];
    fakeEnds = [];
    
    resetGrid();
}

// RESET GRID TO EMPTY
function resetGrid() {
    stopSimulation();
    grid = Array(rows).fill().map(() => Array(cols).fill(0));
    fakeEnds = [];
    
    // Clear stats
    statVisited.innerText = "0";
    statSolutions.innerText = "0";
    statPathLen.innerText = "0";
    statTime.innerText = "0 ms";
    setStatus("ready");
    
    drawGridDOM();
    addLogLine("system-msg", `Grid reset to empty (${rows}x${cols}).`);
}

// RENDER GRID IN DOM
function drawGridDOM() {
    gridContainer.innerHTML = '';
    gridContainer.style.gridTemplateRows = `repeat(${rows}, 1fr)`;
    gridContainer.style.gridTemplateColumns = `repeat(${cols}, 1fr)`;
    
    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
            const cellDiv = document.createElement('div');
            cellDiv.className = 'cell';
            cellDiv.dataset.row = r;
            cellDiv.dataset.col = c;
            cellDiv.id = `cell-${r}-${c}`;
            
            const isStart = (r === startNode[0] && c === startNode[1]);
            const isEnd = (r === endNode[0] && c === endNode[1]);
            const isFake = fakeEnds.some(fe => fe[0] === r && fe[1] === c);

            // Add wall or start/end/fake classes
            if (isStart) {
                cellDiv.classList.add('start');
                cellDiv.innerHTML = '<i class="fa-solid fa-location-dot" style="font-size: 0.8rem; color: #040815;"></i>';
            } else if (isEnd) {
                cellDiv.classList.add('end');
                cellDiv.innerHTML = '<i class="fa-solid fa-crosshairs" style="font-size: 0.8rem; color: #fff;"></i>';
            } else if (isFake) {
                cellDiv.classList.add('fake-end');
                cellDiv.innerHTML = '<i class="fa-solid fa-circle-xmark" style="font-size: 0.8rem; color: #fff;"></i>';
            } else if (grid[r][c] === 1) {
                cellDiv.classList.add('wall');
            }
            
            // EVENT LISTENERS FOR INTERACTIVE DRAWING
            cellDiv.addEventListener('mousedown', (e) => handleMouseDown(e, r, c));
            cellDiv.addEventListener('mouseenter', () => handleMouseEnter(r, c));
            cellDiv.addEventListener('dragstart', (e) => e.preventDefault());
            
            gridContainer.appendChild(cellDiv);
        }
    }
    
    window.addEventListener('mouseup', handleMouseUp);
}

// MOUSE INTERACTION LOGIC
function handleMouseDown(e, r, c) {
    if (isSolving) return;
    
    const isStart = (r === startNode[0] && c === startNode[1]);
    const isEnd = (r === endNode[0] && c === endNode[1]);
    const isFake = fakeEnds.some(fe => fe[0] === r && fe[1] === c);

    if (activeTool === 'start') {
        if (!isEnd && grid[r][c] !== 1) {
            moveNode('start', r, c);
        }
    } else if (activeTool === 'end') {
        if (!isStart && grid[r][c] !== 1) {
            moveNode('end', r, c);
        }
    } else if (activeTool === 'fake') {
        if (!isStart && !isEnd && grid[r][c] !== 1) {
            if (isFake) {
                fakeEnds = fakeEnds.filter(fe => !(fe[0] === r && fe[1] === c));
            } else {
                fakeEnds.push([r, c]);
            }
            drawGridDOM();
        }
    } else if (activeTool === 'wall') {
        if (isStart) {
            isDraggingStart = true;
        } else if (isEnd) {
            isDraggingEnd = true;
        } else {
            isDrawingWalls = (grid[r][c] === 0);
            isErasingWalls = (grid[r][c] === 1);
            toggleWallCell(r, c);
        }
    }
}

function handleMouseEnter(r, c) {
    if (isSolving) return;
    
    const cellDiv = document.getElementById(`cell-${r}-${c}`);
    if (isDraggingStart) {
        if (grid[r][c] !== 1 && !(r === endNode[0] && c === endNode[1])) {
            moveNode('start', r, c);
        }
    } else if (isDraggingEnd) {
        if (grid[r][c] !== 1 && !(r === startNode[0] && c === startNode[1])) {
            moveNode('end', r, c);
        }
    } else if (activeTool === 'wall') {
        if (isDrawingWalls) {
            if (!(r === startNode[0] && c === startNode[1]) && !(r === endNode[0] && c === endNode[1])) {
                grid[r][c] = 1;
                // Remove from fake targets if wall painted over it
                fakeEnds = fakeEnds.filter(fe => !(fe[0] === r && fe[1] === c));
                cellDiv.className = 'cell wall';
                cellDiv.innerHTML = '';
            }
        } else if (isErasingWalls) {
            grid[r][c] = 0;
            cellDiv.className = 'cell';
            cellDiv.innerHTML = '';
        }
    }
}

function handleMouseUp() {
    isDraggingStart = false;
    isDraggingEnd = false;
    isDrawingWalls = false;
    isErasingWalls = false;
}

function toggleWallCell(r, c) {
    const cellDiv = document.getElementById(`cell-${r}-${c}`);
    if (grid[r][c] === 0) {
        grid[r][c] = 1;
        // Remove from fake targets if wall painted over it
        fakeEnds = fakeEnds.filter(fe => !(fe[0] === r && fe[1] === c));
        cellDiv.className = 'cell wall';
        cellDiv.innerHTML = '';
    } else {
        grid[r][c] = 0;
        cellDiv.className = 'cell';
        cellDiv.innerHTML = '';
    }
}

function moveNode(type, r, c) {
    const oldNode = type === 'start' ? startNode : endNode;
    const oldCellDiv = document.getElementById(`cell-${oldNode[0]}-${oldNode[1]}`);
    if (oldCellDiv) {
        oldCellDiv.className = 'cell';
        oldCellDiv.innerHTML = '';
    }
    
    if (type === 'start') {
        startNode = [r, c];
    } else {
        endNode = [r, c];
    }
    
    const newCellDiv = document.getElementById(`cell-${r}-${c}`);
    if (newCellDiv) {
        // Ensure not wall or fake target
        newCellDiv.className = `cell ${type}`;
        grid[r][c] = 0;
        fakeEnds = fakeEnds.filter(fe => !(fe[0] === r && fe[1] === c));
        
        if (type === 'start') {
            newCellDiv.innerHTML = '<i class="fa-solid fa-location-dot" style="font-size: 0.8rem; color: #040815;"></i>';
        } else {
            newCellDiv.innerHTML = '<i class="fa-solid fa-crosshairs" style="font-size: 0.8rem; color: #fff;"></i>';
        }
    }
}

// MAZE GENERATION
async function generateMazeFromBackend() {
    if (isSolving) return;
    
    setStatus("ready");
    const pattern = patternSelect.value;
    const patternName = patternSelect.options[patternSelect.selectedIndex].text;
    const loopRate = parseFloat(loopSlider.value) / 100.0;
    addLogLine("system-msg", `Generating pattern "${patternName}" with loop density: ${loopSlider.value}%...`);
    
    try {
        const response = await fetch(`/api/generate?rows=${rows}&cols=${cols}&pattern=${pattern}&loopRate=${loopRate}`);
        const data = await response.json();
        
        grid = data.grid;
        fakeEnds = data.fakeEnds || [];
        
        // Make sure start and end nodes are clear path
        grid[startNode[0]][startNode[1]] = 0;
        grid[endNode[0]][endNode[1]] = 0;
        
        drawGridDOM();
        addLogLine("system-msg", `Puzzle generated successfully. Placed ${fakeEnds.length} Fake Target traps.`);
    } catch (e) {
        console.error(e);
        addLogLine("system-msg", "Error: Failed to contact Java maze generator.");
    }
}

// CLEAR VISUAL MARKS KEEPING WALLS & TRAPS
function clearVisuals() {
    stopSimulation();
    
    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
            const cellDiv = document.getElementById(`cell-${r}-${c}`);
            if (cellDiv) {
                cellDiv.classList.remove('visited', 'visited-reverse', 'backtracked', 'path', 'path-overlay');
                if (!cellDiv.classList.contains('fake-end') && !cellDiv.classList.contains('wall') && !cellDiv.classList.contains('start') && !cellDiv.classList.contains('end')) {
                    cellDiv.style.backgroundColor = '';
                }
            }
        }
    }
    
    allPaths = [];
    multiPathContainer.style.display = 'none';
    overlayPathsCheck.checked = false;
    
    statVisited.innerText = "0";
    statSolutions.innerText = "0";
    statPathLen.innerText = "0";
    statTime.innerText = "0 ms";
    setStatus("ready");
    addLogLine("system-msg", "Path visualizations cleared.");
}

// SET UI STATUS
function setStatus(status) {
    statusText.className = "status-badge " + status;
    statusText.innerText = status.toUpperCase();
    
    if (status === "ready") {
        solveBtn.innerHTML = '<i class="fa-solid fa-play"></i> Solve Maze';
        solveBtn.className = "btn btn-primary";
        toggleControlDisable(false);
    } else if (status === "exploring" || status === "backtracking") {
        solveBtn.innerHTML = '<i class="fa-solid fa-stop"></i> Stop Solver';
        solveBtn.className = "btn btn-danger";
        toggleControlDisable(true);
    } else {
        solveBtn.innerHTML = '<i class="fa-solid fa-rotate-left"></i> Reset Path';
        solveBtn.className = "btn btn-secondary";
        toggleControlDisable(false);
    }
}

function toggleControlDisable(disabled) {
    algoSelect.disabled = disabled;
    patternSelect.disabled = disabled;
    sizeSelect.disabled = disabled;
    loopSlider.disabled = disabled;
    generateBtn.disabled = disabled;
    clearGridBtn.disabled = disabled;
    clearPathBtn.disabled = disabled;
}

// TERMINAL LOG HELPER
function addLogLine(type, message) {
    const line = document.createElement('div');
    line.className = `log-line ${type}`;
    
    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    line.innerHTML = `<span style="color:#475569; font-size:0.7rem; margin-right:6px;">[${time}]</span> ${message}`;
    
    logOutput.appendChild(line);
    
    // Prune DOM tree to avoid browser crash/lag on dense 51x51 runs
    while (logOutput.childNodes.length > 50) {
        logOutput.removeChild(logOutput.firstChild);
    }
    
    logOutput.scrollTop = logOutput.scrollHeight;
}

// SOLVE SIMULATOR RUNNER
function toggleSolve() {
    if (statusText.innerText === "SOLVED" || statusText.innerText === "NOPATH") {
        clearVisuals();
        return;
    }
    
    if (isSolving) {
        stopSimulation();
        setStatus("ready");
        addLogLine("system-msg", "Pathfinding simulation stopped by user.");
    } else {
        startSolve();
    }
}

function stopSimulation() {
    isSolving = false;
    if (animationTimeoutId) {
        clearTimeout(animationTimeoutId);
        animationTimeoutId = null;
    }
}

async function startSolve() {
    isSolving = true;
    const algo = algoSelect.value;
    setStatus("exploring");
    addLogLine("system-msg", `Running Java Solver (${algoSelect.options[algoSelect.selectedIndex].text})...`);
    
    // Clear path decorations before starting
    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
            const cellDiv = document.getElementById(`cell-${r}-${c}`);
            if (cellDiv) {
                cellDiv.classList.remove('visited', 'visited-reverse', 'backtracked', 'path', 'path-overlay');
                if (!cellDiv.classList.contains('fake-end') && !cellDiv.classList.contains('wall') && !cellDiv.classList.contains('start') && !cellDiv.classList.contains('end')) {
                    cellDiv.style.backgroundColor = '';
                }
            }
        }
    }
    
    allPaths = [];
    multiPathContainer.style.display = 'none';
    overlayPathsCheck.checked = false;

    try {
        const payload = {
            grid: grid,
            start: startNode,
            end: endNode,
            fakeEnds: fakeEnds,
            algorithm: algo
        };
        
        const response = await fetch('/api/solve', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        
        const data = await response.json();
        
        if (data.error) {
            addLogLine("system-msg", `Error from backend: ${data.error}`);
            stopSimulation();
            setStatus("ready");
            return;
        }

        addLogLine("system-msg", `Java solver completed in ${data.executionTimeMs} ms.`);
        activeTrace = data.trace;
        currentTraceIndex = 0;
        
        animateTrace(data);
    } catch (e) {
        console.error(e);
        addLogLine("system-msg", "Error: Failed to contact Java solver server.");
        stopSimulation();
        setStatus("ready");
    }
}

// STEP-BY-STEP SIMULATION ANIMATION
function animateTrace(solveResult) {
    if (!isSolving) return;

    if (currentTraceIndex >= activeTrace.length) {
        displayFinalPath(solveResult);
        return;
    }

    const step = activeTrace[currentTraceIndex];
    const cellDiv = document.getElementById(`cell-${step.r}-${step.c}`);
    
    if (cellDiv && !(step.r === startNode[0] && step.c === startNode[1]) && !(step.r === endNode[0] && step.c === endNode[1])) {
        if (step.type === 'visit' || step.type === 'visit_forward') {
            cellDiv.classList.remove('backtracked', 'visited-reverse');
            cellDiv.classList.add('visited');
            setStatus("exploring");
        } else if (step.type === 'visit_reverse') {
            cellDiv.classList.remove('backtracked', 'visited');
            cellDiv.classList.add('visited-reverse');
            setStatus("exploring");
        } else if (step.type === 'backtrack') {
            cellDiv.classList.remove('visited', 'visited-reverse');
            cellDiv.classList.add('backtracked');
            setStatus("backtracking");
        } else if (step.type === 'fake_end') {
            cellDiv.classList.remove('visited', 'visited-reverse', 'backtracked');
            cellDiv.classList.add('fake-end');
            cellDiv.style.backgroundColor = '#ef4444';
            setTimeout(() => { cellDiv.style.backgroundColor = ''; }, 300);
            setStatus("backtracking");
        } else if (step.type === 'solution') {
            cellDiv.classList.add('visited');
            cellDiv.style.backgroundColor = '#22c55e';
            setTimeout(() => { cellDiv.style.backgroundColor = ''; }, 200);
            
            const match = step.description.match(/#(\d+)/);
            if (match) {
                statSolutions.innerText = match[1];
            }
        }
    }
    
    addLogLine(step.type, step.description);
    
    currentTraceIndex++;
    animationTimeoutId = setTimeout(() => {
        animateTrace(solveResult);
    }, simulationSpeed);
}

// DRAW FINAL SHORTEST PATH & POPULATE MULTI-PATH
function displayFinalPath(solveResult) {
    stopSimulation();
    
    statVisited.innerText = solveResult.visitedCount;
    statSolutions.innerText = solveResult.solutionsCount;
    statTime.innerText = `${solveResult.executionTimeMs} ms`;
    
    allPaths = solveResult.allPaths || [];
    
    if (solveResult.success) {
        setStatus("solved");
        
        const shortest = solveResult.shortestPath;
        statPathLen.innerText = shortest.length;
        
        if (allPaths.length > 1) {
            addLogLine("system-msg", `Discovered ${allPaths.length} alternative path variations.`);
            multiPathContainer.style.display = 'flex';
            
            pathSelect.innerHTML = '';
            allPaths.forEach((path, idx) => {
                const opt = document.createElement('option');
                opt.value = idx;
                opt.innerText = `Solution Path #${idx + 1} (Length: ${path.length})`;
                pathSelect.appendChild(opt);
            });
            
            pathSelect.onchange = (e) => {
                highlightSelectedPath(parseInt(e.target.value));
            };
            
            overlayPathsCheck.onchange = (e) => {
                toggleAllPathsOverlay(e.target.checked);
            };
        }
        
        addLogLine("system-msg", `Solved! Drawing primary shortest path...`);
        animatePathLine(shortest, 0);
    } else {
        setStatus("nopath");
        statPathLen.innerText = "0";
        addLogLine("system-msg", `Failed: No valid path exists between start and end node.`);
    }
}

function animatePathLine(path, index) {
    if (index >= path.length) {
        addLogLine("system-msg", "Simulation completed.");
        return;
    }
    
    const coord = path[index];
    const cellDiv = document.getElementById(`cell-${coord[0]}-${coord[1]}`);
    
    if (cellDiv && !(coord[0] === startNode[0] && coord[1] === startNode[1]) && !(coord[0] === endNode[0] && coord[1] === endNode[1])) {
        cellDiv.classList.remove('visited', 'visited-reverse', 'backtracked');
        cellDiv.classList.add('path');
    }
    
    setTimeout(() => {
        animatePathLine(path, index + 1);
    }, Math.max(12, simulationSpeed / 2));
}

// HIGHLIGHT SELECT SOLUTION PATH (ONE BY ONE)
function highlightSelectedPath(pathIndex) {
    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
            const cellDiv = document.getElementById(`cell-${r}-${c}`);
            if (cellDiv) {
                cellDiv.classList.remove('path');
            }
        }
    }
    
    const path = allPaths[pathIndex];
    if (!path) return;
    
    statPathLen.innerText = path.length;
    
    path.forEach(coord => {
        const cellDiv = document.getElementById(`cell-${coord[0]}-${coord[1]}`);
        if (cellDiv && !(coord[0] === startNode[0] && coord[1] === startNode[1]) && !(coord[0] === endNode[0] && coord[1] === endNode[1])) {
            cellDiv.classList.remove('visited', 'visited-reverse', 'backtracked');
            cellDiv.classList.add('path');
        }
    });
    
    addLogLine("system-msg", `Switched to Solution Path #${pathIndex + 1}.`);
}

// TOGGLE CONCURRENT MULTI-PATH OVERLAY (SHOW ALL SOLUTIONS IN GOLD GRADIENT)
function toggleAllPathsOverlay(show) {
    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
            const cellDiv = document.getElementById(`cell-${r}-${c}`);
            if (cellDiv) {
                cellDiv.classList.remove('path-overlay');
            }
        }
    }
    
    if (!show) {
        addLogLine("system-msg", "Alternative path overlay disabled.");
        return;
    }
    
    allPaths.forEach((path) => {
        path.forEach(coord => {
            const cellDiv = document.getElementById(`cell-${coord[0]}-${coord[1]}`);
            if (cellDiv && 
                !(coord[0] === startNode[0] && coord[1] === startNode[1]) && 
                !(coord[0] === endNode[0] && coord[1] === endNode[1]) && 
                !cellDiv.classList.contains('path')) {
                cellDiv.classList.add('path-overlay');
            }
        });
    });
    
    addLogLine("system-msg", `Overlaid all ${allPaths.length} discovered path variations.`);
}
