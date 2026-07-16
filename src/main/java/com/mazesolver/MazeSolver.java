package com.mazesolver;

import java.util.*;

public class MazeSolver {

    public static class SolveResult {
        public boolean success;
        public List<TraceStep> trace;
        public int solutionsCount;
        public List<int[]> shortestPath;
        public List<List<int[]>> allPaths;
        public int visitedCount;
        public long executionTimeMs;

        public SolveResult() {
            this.trace = new ArrayList<>();
            this.shortestPath = new ArrayList<>();
            this.allPaths = new ArrayList<>();
        }

        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\":").append(success).append(",");
            json.append("\"solutionsCount\":").append(solutionsCount).append(",");
            json.append("\"visitedCount\":").append(visitedCount).append(",");
            json.append("\"executionTimeMs\":").append(executionTimeMs).append(",");
            
            // Serialize shortestPath
            json.append("\"shortestPath\":[");
            for (int i = 0; i < shortestPath.size(); i++) {
                int[] p = shortestPath.get(i);
                json.append("[").append(p[0]).append(",").append(p[1]).append("]");
                if (i < shortestPath.size() - 1) json.append(",");
            }
            json.append("],");

            // Serialize allPaths
            json.append("\"allPaths\":[");
            for (int i = 0; i < allPaths.size(); i++) {
                List<int[]> path = allPaths.get(i);
                json.append("[");
                for (int j = 0; j < path.size(); j++) {
                    int[] p = path.get(j);
                    json.append("[").append(p[0]).append(",").append(p[1]).append("]");
                    if (j < path.size() - 1) json.append(",");
                }
                json.append("]");
                if (i < allPaths.size() - 1) json.append(",");
            }
            json.append("],");

            // Serialize trace
            json.append("\"trace\":[");
            for (int i = 0; i < trace.size(); i++) {
                json.append(trace.get(i).toJson());
                if (i < trace.size() - 1) json.append(",");
            }
            json.append("]");
            json.append("}");
            return json.toString();
        }
    }

    public static SolveResult solve(int[][] grid, int startR, int startC, int endR, int endC, List<int[]> fakeEnds, String algorithm) {
        long startTime = System.currentTimeMillis();
        SolveResult result = new SolveResult();
        
        if (algorithm.equalsIgnoreCase("backtracking")) {
            runBacktracking(grid, startR, startC, endR, endC, fakeEnds, result);
            // Sort DFS paths by length (shortest path is first, alternative paths follow)
            result.allPaths.sort(Comparator.comparingInt(List::size));
        } else {
            // 1. Run the base pathfinding algorithm to get primary shortest path
            if (algorithm.equalsIgnoreCase("bfs")) {
                runBFS(grid, startR, startC, endR, endC, fakeEnds, result);
            } else if (algorithm.equalsIgnoreCase("astar")) {
                runAStar(grid, startR, startC, endR, endC, fakeEnds, result);
            } else if (algorithm.equalsIgnoreCase("dijkstra")) {
                runDijkstra(grid, startR, startC, endR, endC, fakeEnds, result);
            } else if (algorithm.equalsIgnoreCase("greedy")) {
                runGreedyBestFirst(grid, startR, startC, endR, endC, fakeEnds, result);
            } else if (algorithm.equalsIgnoreCase("bibfs")) {
                runBidirectionalBFS(grid, startR, startC, endR, endC, fakeEnds, result);
            } else if (algorithm.equalsIgnoreCase("biastar")) {
                runBidirectionalAStar(grid, startR, startC, endR, endC, fakeEnds, result);
            }
            
            // 2. If base run is successful, automatically compute alternative path variations
            if (result.success && !result.shortestPath.isEmpty()) {
                result.allPaths = findKAlternativePaths(grid, startR, startC, endR, endC, fakeEnds, algorithm, result.shortestPath, 15);
                result.solutionsCount = result.allPaths.size();
            }
        }

        result.executionTimeMs = System.currentTimeMillis() - startTime;
        return result;
    }

    // Yen's-based alternative pathfinder: selectively blocks nodes of current shortest path
    private static List<List<int[]>> findKAlternativePaths(int[][] grid, int startR, int startC, int endR, int endC, 
                                                           List<int[]> fakeEnds, String algorithm, List<int[]> shortestPath, int maxPaths) {
        List<List<int[]>> results = new ArrayList<>();
        if (shortestPath == null || shortestPath.isEmpty()) {
            return results;
        }

        // Add 1st best path
        results.add(new ArrayList<>(shortestPath));

        Set<String> pathSignatures = new HashSet<>();
        pathSignatures.add(getPathSignature(shortestPath));

        // Block each intermediate node of the primary shortest path one-by-one to force detours
        for (int i = 1; i < shortestPath.size() - 1; i++) {
            if (results.size() >= maxPaths) break;

            int[] blockNode = shortestPath.get(i);
            int originalVal = grid[blockNode[0]][blockNode[1]];
            
            // Set cell temporarily to Wall
            grid[blockNode[0]][blockNode[1]] = 1;

            SolveResult tempResult = new SolveResult();
            if (algorithm.equalsIgnoreCase("bfs")) {
                runBFS(grid, startR, startC, endR, endC, fakeEnds, tempResult);
            } else if (algorithm.equalsIgnoreCase("astar")) {
                runAStar(grid, startR, startC, endR, endC, fakeEnds, tempResult);
            } else if (algorithm.equalsIgnoreCase("dijkstra")) {
                runDijkstra(grid, startR, startC, endR, endC, fakeEnds, tempResult);
            } else if (algorithm.equalsIgnoreCase("greedy")) {
                runGreedyBestFirst(grid, startR, startC, endR, endC, fakeEnds, tempResult);
            } else if (algorithm.equalsIgnoreCase("bibfs")) {
                runBidirectionalBFS(grid, startR, startC, endR, endC, fakeEnds, tempResult);
            } else if (algorithm.equalsIgnoreCase("biastar")) {
                runBidirectionalAStar(grid, startR, startC, endR, endC, fakeEnds, tempResult);
            }

            // Restore original cell value
            grid[blockNode[0]][blockNode[1]] = originalVal;

            if (tempResult.success && !tempResult.shortestPath.isEmpty()) {
                String sig = getPathSignature(tempResult.shortestPath);
                if (!pathSignatures.contains(sig)) {
                    pathSignatures.add(sig);
                    results.add(tempResult.shortestPath);
                }
            }
        }

        // Secondary block sweep along the 2nd best path to discover further detours
        if (results.size() < maxPaths && results.size() > 1) {
            List<int[]> secondPath = results.get(1);
            for (int i = 1; i < secondPath.size() - 1; i++) {
                if (results.size() >= maxPaths) break;

                int[] blockNode = secondPath.get(i);
                int originalVal = grid[blockNode[0]][blockNode[1]];
                grid[blockNode[0]][blockNode[1]] = 1;

                SolveResult tempResult = new SolveResult();
                if (algorithm.equalsIgnoreCase("bfs")) {
                    runBFS(grid, startR, startC, endR, endC, fakeEnds, tempResult);
                } else if (algorithm.equalsIgnoreCase("astar")) {
                    runAStar(grid, startR, startC, endR, endC, fakeEnds, tempResult);
                } else if (algorithm.equalsIgnoreCase("dijkstra")) {
                    runDijkstra(grid, startR, startC, endR, endC, fakeEnds, tempResult);
                } else if (algorithm.equalsIgnoreCase("greedy")) {
                    runGreedyBestFirst(grid, startR, startC, endR, endC, fakeEnds, tempResult);
                } else if (algorithm.equalsIgnoreCase("bibfs")) {
                    runBidirectionalBFS(grid, startR, startC, endR, endC, fakeEnds, tempResult);
                } else if (algorithm.equalsIgnoreCase("biastar")) {
                    runBidirectionalAStar(grid, startR, startC, endR, endC, fakeEnds, tempResult);
                }

                grid[blockNode[0]][blockNode[1]] = originalVal;

                if (tempResult.success && !tempResult.shortestPath.isEmpty()) {
                    String sig = getPathSignature(tempResult.shortestPath);
                    if (!pathSignatures.contains(sig)) {
                        pathSignatures.add(sig);
                        results.add(tempResult.shortestPath);
                    }
                }
            }
        }

        // Sort all alternative paths by length (shortest first)
        results.sort(Comparator.comparingInt(List::size));
        return results;
    }

    private static String getPathSignature(List<int[]> path) {
        StringBuilder sb = new StringBuilder();
        for (int[] p : path) {
            sb.append(p[0]).append(",").append(p[1]).append(";");
        }
        return sb.toString();
    }

    private static boolean isValid(int[][] grid, int r, int c) {
        return r >= 0 && r < grid.length && c >= 0 && c < grid[0].length && grid[r][c] == 0;
    }

    private static boolean isFakeEnd(int r, int c, List<int[]> fakeEnds) {
        if (fakeEnds == null) return false;
        for (int[] fe : fakeEnds) {
            if (fe[0] == r && fe[1] == c) return true;
        }
        return false;
    }

    // --- RECURSIVE BACKTRACKING DFS WITH DEAD-END PRUNING ---
    private static void runBacktracking(int[][] grid, int startR, int startC, int endR, int endC, List<int[]> fakeEnds, SolveResult result) {
        int rows = grid.length;
        int cols = grid[0].length;
        boolean[][] visited = new boolean[rows][cols];
        boolean[][] visitedEver = new boolean[rows][cols];
        boolean[][] isDeadEnd = new boolean[rows][cols];
        List<int[]> currentPath = new ArrayList<>();
        int[] solutionsCount = {0};
        int[] uniqueVisited = {0};
        boolean[] firstPathSaved = {false};
        
        long searchStartTime = System.currentTimeMillis();

        dfsAllPaths(grid, startR, startC, endR, endC, fakeEnds, visited, visitedEver, isDeadEnd, currentPath, 
                    result.trace, result, solutionsCount, uniqueVisited, firstPathSaved, searchStartTime);

        result.success = (solutionsCount[0] > 0);
        result.solutionsCount = solutionsCount[0];
        result.visitedCount = uniqueVisited[0];
    }

    private static boolean dfsAllPaths(int[][] grid, int r, int c, int endR, int endC, List<int[]> fakeEnds, 
                                        boolean[][] visited, boolean[][] visitedEver, boolean[][] isDeadEnd, 
                                        List<int[]> currentPath, List<TraceStep> trace, SolveResult result, 
                                        int[] solutionsCount, int[] uniqueVisited, boolean[] firstPathSaved, 
                                        long startTime) {
        if (solutionsCount[0] >= 10000 || (System.currentTimeMillis() - startTime) > 1000) {
            return false;
        }

        if (isDeadEnd[r][c]) {
            return false;
        }

        visited[r][c] = true;
        
        if (!visitedEver[r][c]) {
            visitedEver[r][c] = true;
            uniqueVisited[0]++;
        }
        
        currentPath.add(new int[]{r, c});

        if (isFakeEnd(r, c, fakeEnds)) {
            if (trace.size() < 3000) {
                trace.add(new TraceStep("fake_end", r, c, "Reached Fake Target at (" + r + "," + c + ") - BLOCKED! Backtracking."));
            }
            currentPath.remove(currentPath.size() - 1);
            visited[r][c] = false;
            isDeadEnd[r][c] = true;
            return false;
        }

        if (trace.size() < 3000) {
            trace.add(new TraceStep("visit", r, c, "DFS at (" + r + "," + c + "). Depth: " + currentPath.size()));
        }

        boolean canReachEnd = false;

        if (r == endR && c == endC) {
            solutionsCount[0]++;
            canReachEnd = true;
            
            if (trace.size() < 3000) {
                trace.add(new TraceStep("solution", r, c, "Found solution path #" + solutionsCount[0] + "!"));
            }

            if (result.allPaths.size() < 100) {
                List<int[]> pathCopy = new ArrayList<>();
                for (int[] p : currentPath) {
                    pathCopy.add(new int[]{p[0], p[1]});
                }
                result.allPaths.add(pathCopy);
            }

            if (!firstPathSaved[0]) {
                for (int[] p : currentPath) {
                    result.shortestPath.add(new int[]{p[0], p[1]});
                }
                firstPathSaved[0] = true;
            }
        } else {
            int[] dr = {-1, 0, 1, 0};
            int[] dc = {0, 1, 0, -1};
            for (int i = 0; i < 4; i++) {
                int nr = r + dr[i];
                int nc = c + dc[i];
                if (isValid(grid, nr, nc) && !visited[nr][nc]) {
                    boolean success = dfsAllPaths(grid, nr, nc, endR, endC, fakeEnds, visited, visitedEver, isDeadEnd, 
                                                  currentPath, trace, result, solutionsCount, uniqueVisited, 
                                                  firstPathSaved, startTime);
                    if (success) {
                        canReachEnd = true;
                    }
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        if (trace.size() < 3000) {
            trace.add(new TraceStep("backtrack", r, c, "Backtracking from (" + r + "," + c + ")"));
        }
        visited[r][c] = false;

        if (!canReachEnd) {
            isDeadEnd[r][c] = true;
        }

        return canReachEnd;
    }

    // --- BFS (BREADTH-FIRST SEARCH) ---
    private static void runBFS(int[][] grid, int startR, int startC, int endR, int endC, List<int[]> fakeEnds, SolveResult result) {
        int rows = grid.length;
        int cols = grid[0].length;
        boolean[][] visited = new boolean[rows][cols];
        boolean[][] visitedEver = new boolean[rows][cols];
        Map<Integer, Integer> parentMap = new HashMap<>();

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startR, startC});
        visited[startR][startC] = true;
        result.trace.add(new TraceStep("visit", startR, startC, "Start BFS from (" + startR + "," + startC + ")"));

        boolean found = false;
        int visitedCount = 0;
        int[] dr = {-1, 0, 1, 0};
        int[] dc = {0, 1, 0, -1};

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int r = curr[0];
            int c = curr[1];

            if (isFakeEnd(r, c, fakeEnds)) {
                if (result.trace.size() < 3000) {
                    result.trace.add(new TraceStep("fake_end", r, c, "BFS hit Fake Target at (" + r + "," + c + ") - BLOCKED!"));
                }
                continue;
            }

            if (!visitedEver[r][c]) {
                visitedEver[r][c] = true;
                visitedCount++;
            }

            if (result.trace.size() < 3000) {
                result.trace.add(new TraceStep("visit", r, c, "Pop cell (" + r + "," + c + ")"));
            }

            if (r == endR && c == endC) {
                found = true;
                break;
            }

            for (int i = 0; i < 4; i++) {
                int nr = r + dr[i];
                int nc = c + dc[i];
                if (isValid(grid, nr, nc) && !visited[nr][nc]) {
                    visited[nr][nc] = true;
                    parentMap.put(nr * cols + nc, r * cols + c);
                    queue.add(new int[]{nr, nc});
                }
            }
        }

        result.success = found;
        result.solutionsCount = found ? 1 : 0;
        result.visitedCount = visitedCount;

        if (found) {
            reconstructPath(parentMap, startR, startC, endR, endC, cols, result.shortestPath);
        }
    }

    // --- DIJKSTRA'S ALGORITHM ---
    private static void runDijkstra(int[][] grid, int startR, int startC, int endR, int endC, List<int[]> fakeEnds, SolveResult result) {
        int rows = grid.length;
        int cols = grid[0].length;
        boolean[][] visited = new boolean[rows][cols];
        boolean[][] visitedEver = new boolean[rows][cols];
        int[][] dist = new int[rows][cols];
        for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);
        
        Map<Integer, Integer> parentMap = new HashMap<>();
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
        
        pq.add(new int[]{startR, startC, 0});
        dist[startR][startC] = 0;
        result.trace.add(new TraceStep("visit", startR, startC, "Starting Dijkstra search"));

        boolean found = false;
        int visitedCount = 0;
        int[] dr = {-1, 0, 1, 0};
        int[] dc = {0, 1, 0, -1};

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int r = curr[0];
            int c = curr[1];
            int d = curr[2];

            if (visited[r][c]) continue;
            
            if (isFakeEnd(r, c, fakeEnds)) {
                if (result.trace.size() < 3000) {
                    result.trace.add(new TraceStep("fake_end", r, c, "Dijkstra hit Fake Target at (" + r + "," + c + ") - BLOCKED!"));
                }
                visited[r][c] = true;
                continue;
            }

            visited[r][c] = true;
            
            if (!visitedEver[r][c]) {
                visitedEver[r][c] = true;
                visitedCount++;
            }

            if (result.trace.size() < 3000) {
                result.trace.add(new TraceStep("visit", r, c, "Settled (" + r + "," + c + ") with distance " + d));
            }

            if (r == endR && c == endC) {
                found = true;
                break;
            }

            for (int i = 0; i < 4; i++) {
                int nr = r + dr[i];
                int nc = c + dc[i];
                if (isValid(grid, nr, nc) && !visited[nr][nc]) {
                    int nextDist = d + 1;
                    if (nextDist < dist[nr][nc]) {
                        dist[nr][nc] = nextDist;
                        parentMap.put(nr * cols + nc, r * cols + c);
                        pq.add(new int[]{nr, nc, nextDist});
                    }
                }
            }
        }

        result.success = found;
        result.solutionsCount = found ? 1 : 0;
        result.visitedCount = visitedCount;

        if (found) {
            reconstructPath(parentMap, startR, startC, endR, endC, cols, result.shortestPath);
        }
    }

    // --- GREEDY BEST-FIRST SEARCH ---
    private static void runGreedyBestFirst(int[][] grid, int startR, int startC, int endR, int endC, List<int[]> fakeEnds, SolveResult result) {
        int rows = grid.length;
        int cols = grid[0].length;
        boolean[][] visited = new boolean[rows][cols];
        boolean[][] visitedEver = new boolean[rows][cols];
        Map<Integer, Integer> parentMap = new HashMap<>();
        
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> 
            Math.abs(a[0] - endR) + Math.abs(a[1] - endC)
        ));
        
        pq.add(new int[]{startR, startC});
        visited[startR][startC] = true;
        result.trace.add(new TraceStep("visit", startR, startC, "Starting Greedy BFS"));

        boolean found = false;
        int visitedCount = 0;
        int[] dr = {-1, 0, 1, 0};
        int[] dc = {0, 1, 0, -1};

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int r = curr[0];
            int c = curr[1];

            if (isFakeEnd(r, c, fakeEnds)) {
                if (result.trace.size() < 3000) {
                    result.trace.add(new TraceStep("fake_end", r, c, "Greedy hit Fake Target at (" + r + "," + c + ") - BLOCKED!"));
                }
                continue;
            }

            if (!visitedEver[r][c]) {
                visitedEver[r][c] = true;
                visitedCount++;
            }

            if (result.trace.size() < 3000) {
                int h = Math.abs(r - endR) + Math.abs(c - endC);
                result.trace.add(new TraceStep("visit", r, c, "Pop cell (" + r + "," + c + ") with h=" + h));
            }

            if (r == endR && c == endC) {
                found = true;
                break;
            }

            for (int i = 0; i < 4; i++) {
                int nr = r + dr[i];
                int nc = c + dc[i];
                if (isValid(grid, nr, nc) && !visited[nr][nc]) {
                    visited[nr][nc] = true;
                    parentMap.put(nr * cols + nc, r * cols + c);
                    pq.add(new int[]{nr, nc});
                }
            }
        }

        result.success = found;
        result.solutionsCount = found ? 1 : 0;
        result.visitedCount = visitedCount;

        if (found) {
            reconstructPath(parentMap, startR, startC, endR, endC, cols, result.shortestPath);
        }
    }

    // --- A* SEARCH ---
    private static void runAStar(int[][] grid, int startR, int startC, int endR, int endC, List<int[]> fakeEnds, SolveResult result) {
        int rows = grid.length;
        int cols = grid[0].length;
        boolean[][] closedSet = new boolean[rows][cols];
        boolean[][] visitedEver = new boolean[rows][cols];
        
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>();
        int[][] gScores = new int[rows][cols];
        for (int[] row : gScores) Arrays.fill(row, Integer.MAX_VALUE);

        AStarNode startNode = new AStarNode(startR, startC, 0, Math.abs(startR - endR) + Math.abs(startC - endC), null);
        openSet.add(startNode);
        gScores[startR][startC] = 0;

        result.trace.add(new TraceStep("visit", startR, startC, "Starting A* Search"));

        boolean found = false;
        int visitedCount = 0;
        int[] dr = {-1, 0, 1, 0};
        int[] dc = {0, 1, 0, -1};

        while (!openSet.isEmpty()) {
            AStarNode curr = openSet.poll();
            if (closedSet[curr.r][curr.c]) continue;
            
            if (isFakeEnd(curr.r, curr.c, fakeEnds)) {
                if (result.trace.size() < 3000) {
                    result.trace.add(new TraceStep("fake_end", curr.r, curr.c, "A* hit Fake Target at (" + curr.r + "," + curr.c + ") - BLOCKED!"));
                }
                closedSet[curr.r][curr.c] = true;
                continue;
            }

            closedSet[curr.r][curr.c] = true;
            
            if (!visitedEver[curr.r][curr.c]) {
                visitedEver[curr.r][curr.c] = true;
                visitedCount++;
            }

            if (result.trace.size() < 3000) {
                result.trace.add(new TraceStep("visit", curr.r, curr.c, 
                    "A* Pop cell (" + curr.r + "," + curr.c + ") with f=" + curr.f));
            }

            if (curr.r == endR && curr.c == endC) {
                found = true;
                
                AStarNode temp = curr;
                while (temp != null) {
                    result.shortestPath.add(0, new int[]{temp.r, temp.c});
                    temp = temp.parent;
                }
                break;
            }

            for (int i = 0; i < 4; i++) {
                int nr = curr.r + dr[i];
                int nc = curr.c + dc[i];
                if (isValid(grid, nr, nc) && !closedSet[nr][nc]) {
                    int tentativeG = curr.g + 1;
                    if (tentativeG < gScores[nr][nc]) {
                        gScores[nr][nc] = tentativeG;
                        int h = Math.abs(nr - endR) + Math.abs(nc - endC);
                        AStarNode neighborNode = new AStarNode(nr, nc, tentativeG, h, curr);
                        openSet.add(neighborNode);
                    }
                }
            }
        }

        result.success = found;
        result.solutionsCount = found ? 1 : 0;
        result.visitedCount = visitedCount;
    }

    private static class AStarNode implements Comparable<AStarNode> {
        int r, c;
        int g;
        int h;
        int f;
        AStarNode parent;

        AStarNode(int r, int c, int g, int h, AStarNode parent) {
            this.r = r;
            this.c = c;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.parent = parent;
        }

        @Override
        public int compareTo(AStarNode o) {
            return Integer.compare(this.f, o.f);
        }
    }

    // --- BIDIRECTIONAL BFS ---
    private static void runBidirectionalBFS(int[][] grid, int startR, int startC, int endR, int endC, List<int[]> fakeEnds, SolveResult result) {
        int rows = grid.length;
        int cols = grid[0].length;
        
        Queue<int[]> qStart = new LinkedList<>();
        Queue<int[]> qEnd = new LinkedList<>();
        
        Map<Integer, Integer> parentStart = new HashMap<>();
        Map<Integer, Integer> parentEnd = new HashMap<>();
        boolean[][] visitedEver = new boolean[rows][cols];
        
        qStart.add(new int[]{startR, startC});
        parentStart.put(startR * cols + startC, -1);
        
        qEnd.add(new int[]{endR, endC});
        parentEnd.put(endR * cols + endC, -1);
        
        result.trace.add(new TraceStep("visit", startR, startC, "Bidirectional BFS: Start from Start node"));
        result.trace.add(new TraceStep("visit_reverse", endR, endC, "Bidirectional BFS: Start from End node"));
        
        int visitedCount = 0;
        boolean found = false;
        int intersectNode = -1;
        
        int[] dr = {-1, 0, 1, 0};
        int[] dc = {0, 1, 0, -1};
        
        while (!qStart.isEmpty() && !qEnd.isEmpty()) {
            if (!qStart.isEmpty()) {
                int[] curr = qStart.poll();
                int r = curr[0];
                int c = curr[1];
                int nodeKey = r * cols + c;

                if (isFakeEnd(r, c, fakeEnds)) {
                    if (result.trace.size() < 3000) {
                        result.trace.add(new TraceStep("fake_end", r, c, "Bi-BFS forward hit Fake Target at (" + r + "," + c + ") - BLOCKED!"));
                    }
                    continue;
                }

                if (!visitedEver[r][c]) {
                    visitedEver[r][c] = true;
                    visitedCount++;
                }
                
                if (result.trace.size() < 3000) {
                    result.trace.add(new TraceStep("visit", r, c, "Forward queue pop: (" + r + "," + c + ")"));
                }
                
                if (parentEnd.containsKey(nodeKey)) {
                    found = true;
                    intersectNode = nodeKey;
                    break;
                }
                
                for (int i = 0; i < 4; i++) {
                    int nr = r + dr[i];
                    int nc = c + dc[i];
                    int neighborKey = nr * cols + nc;
                    if (isValid(grid, nr, nc) && !parentStart.containsKey(neighborKey)) {
                        parentStart.put(neighborKey, nodeKey);
                        qStart.add(new int[]{nr, nc});
                    }
                }
            }
            
            if (!qEnd.isEmpty()) {
                int[] curr = qEnd.poll();
                int r = curr[0];
                int c = curr[1];
                int nodeKey = r * cols + c;

                if (isFakeEnd(r, c, fakeEnds)) {
                    if (result.trace.size() < 3000) {
                        result.trace.add(new TraceStep("fake_end", r, c, "Bi-BFS reverse hit Fake Target at (" + r + "," + c + ") - BLOCKED!"));
                    }
                    continue;
                }

                if (!visitedEver[r][c]) {
                    visitedEver[r][c] = true;
                    visitedCount++;
                }
                
                if (result.trace.size() < 3000) {
                    result.trace.add(new TraceStep("visit_reverse", r, c, "Reverse queue pop: (" + r + "," + c + ")"));
                }
                
                if (parentStart.containsKey(nodeKey)) {
                    found = true;
                    intersectNode = nodeKey;
                    break;
                }
                
                for (int i = 0; i < 4; i++) {
                    int nr = r + dr[i];
                    int nc = c + dc[i];
                    int neighborKey = nr * cols + nc;
                    if (isValid(grid, nr, nc) && !parentEnd.containsKey(neighborKey)) {
                        parentEnd.put(neighborKey, nodeKey);
                        qEnd.add(new int[]{nr, nc});
                    }
                }
            }
        }
        
        result.success = found;
        result.solutionsCount = found ? 1 : 0;
        result.visitedCount = visitedCount;
        
        if (found) {
            reconstructBiPath(parentStart, parentEnd, intersectNode, cols, result.shortestPath);
        }
    }

    // --- BIDIRECTIONAL A* ---
    private static void runBidirectionalAStar(int[][] grid, int startR, int startC, int endR, int endC, List<int[]> fakeEnds, SolveResult result) {
        int rows = grid.length;
        int cols = grid[0].length;
        
        PriorityQueue<BiAStarNode> pqStart = new PriorityQueue<>();
        PriorityQueue<BiAStarNode> pqEnd = new PriorityQueue<>();
        
        Map<Integer, Integer> parentStart = new HashMap<>();
        Map<Integer, Integer> parentEnd = new HashMap<>();
        boolean[][] visitedEver = new boolean[rows][cols];
        
        Map<Integer, Integer> gStart = new HashMap<>();
        Map<Integer, Integer> gEnd = new HashMap<>();
        
        int startKey = startR * cols + startC;
        int endKey = endR * cols + endC;
        
        pqStart.add(new BiAStarNode(startR, startC, 0, Math.abs(startR - endR) + Math.abs(startC - endC)));
        parentStart.put(startKey, -1);
        gStart.put(startKey, 0);
        
        pqEnd.add(new BiAStarNode(endR, endC, 0, Math.abs(endR - startR) + Math.abs(endC - startC)));
        parentEnd.put(endKey, -1);
        gEnd.put(endKey, 0);
        
        result.trace.add(new TraceStep("visit", startR, startC, "Bidirectional A*: Start from Start node"));
        result.trace.add(new TraceStep("visit_reverse", endR, endC, "Bidirectional A*: Start from End node"));
        
        int visitedCount = 0;
        boolean found = false;
        int intersectNode = -1;
        
        int[] dr = {-1, 0, 1, 0};
        int[] dc = {0, 1, 0, -1};
        
        while (!pqStart.isEmpty() && !pqEnd.isEmpty()) {
            if (!pqStart.isEmpty()) {
                BiAStarNode curr = pqStart.poll();
                int r = curr.r;
                int c = curr.c;
                int nodeKey = r * cols + c;
                
                if (isFakeEnd(r, c, fakeEnds)) {
                    if (result.trace.size() < 3000) {
                        result.trace.add(new TraceStep("fake_end", r, c, "Bi-A* forward hit Fake Target at (" + r + "," + c + ") - BLOCKED!"));
                    }
                    continue;
                }

                if (parentEnd.containsKey(nodeKey)) {
                    found = true;
                    intersectNode = nodeKey;
                    break;
                }
                
                if (!visitedEver[r][c]) {
                    visitedEver[r][c] = true;
                    visitedCount++;
                }

                if (result.trace.size() < 3000) {
                    result.trace.add(new TraceStep("visit", r, c, "Bi-A* Forward pop cell (" + r + "," + c + ") with f=" + curr.f));
                }
                
                for (int i = 0; i < 4; i++) {
                    int nr = r + dr[i];
                    int nc = c + dc[i];
                    int neighborKey = nr * cols + nc;
                    if (isValid(grid, nr, nc)) {
                        int tentativeG = gStart.get(nodeKey) + 1;
                        if (!gStart.containsKey(neighborKey) || tentativeG < gStart.get(neighborKey)) {
                            gStart.put(neighborKey, tentativeG);
                            parentStart.put(neighborKey, nodeKey);
                            int h = Math.abs(nr - endR) + Math.abs(nc - endC);
                            pqStart.add(new BiAStarNode(nr, nc, tentativeG, h));
                        }
                    }
                }
            }
            
            if (!pqEnd.isEmpty()) {
                BiAStarNode curr = pqEnd.poll();
                int r = curr.r;
                int c = curr.c;
                int nodeKey = r * cols + c;
                
                if (isFakeEnd(r, c, fakeEnds)) {
                    if (result.trace.size() < 3000) {
                        result.trace.add(new TraceStep("fake_end", r, c, "Bi-A* reverse hit Fake Target at (" + r + "," + c + ") - BLOCKED!"));
                    }
                    continue;
                }

                if (parentStart.containsKey(nodeKey)) {
                    found = true;
                    intersectNode = nodeKey;
                    break;
                }
                
                if (!visitedEver[r][c]) {
                    visitedEver[r][c] = true;
                    visitedCount++;
                }

                if (result.trace.size() < 3000) {
                    result.trace.add(new TraceStep("visit_reverse", r, c, "Bi-A* Reverse pop cell (" + r + "," + c + ") with f=" + curr.f));
                }
                
                for (int i = 0; i < 4; i++) {
                    int nr = r + dr[i];
                    int nc = c + dc[i];
                    int neighborKey = nr * cols + nc;
                    if (isValid(grid, nr, nc)) {
                        int tentativeG = gEnd.get(nodeKey) + 1;
                        if (!gEnd.containsKey(neighborKey) || tentativeG < gEnd.get(neighborKey)) {
                            gEnd.put(neighborKey, tentativeG);
                            parentEnd.put(neighborKey, nodeKey);
                            int h = Math.abs(nr - startR) + Math.abs(nc - startC);
                            pqEnd.add(new BiAStarNode(nr, nc, tentativeG, h));
                        }
                    }
                }
            }
        }
        
        result.success = found;
        result.solutionsCount = found ? 1 : 0;
        result.visitedCount = visitedCount;
        
        if (found) {
            reconstructBiPath(parentStart, parentEnd, intersectNode, cols, result.shortestPath);
        }
    }

    private static class BiAStarNode implements Comparable<BiAStarNode> {
        int r, c;
        int g;
        int h;
        int f;

        BiAStarNode(int r, int c, int g, int h) {
            this.r = r;
            this.c = c;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }

        @Override
        public int compareTo(BiAStarNode o) {
            return Integer.compare(this.f, o.f);
        }
    }

    // --- UTILITIES FOR PATH RECONSTRUCTION ---
    private static void reconstructPath(Map<Integer, Integer> parentMap, int startR, int startC, int endR, int endC, int cols, List<int[]> shortestPath) {
        int curr = endR * cols + endC;
        int start = startR * cols + startC;
        while (curr != start) {
            int cr = curr / cols;
            int cc = curr % cols;
            shortestPath.add(0, new int[]{cr, cc});
            curr = parentMap.get(curr);
        }
        shortestPath.add(0, new int[]{startR, startC});
    }

    private static void reconstructBiPath(Map<Integer, Integer> parentStart, Map<Integer, Integer> parentEnd, int intersectNode, int cols, List<int[]> shortestPath) {
        List<int[]> fromStart = new ArrayList<>();
        Integer curr = intersectNode;
        while (curr != null && curr != -1) {
            int cr = curr / cols;
            int cc = curr % cols;
            fromStart.add(0, new int[]{cr, cc});
            curr = parentStart.get(curr);
        }
        
        List<int[]> fromEnd = new ArrayList<>();
        curr = parentEnd.get(intersectNode);
        while (curr != null && curr != -1) {
            int cr = curr / cols;
            int cc = curr % cols;
            fromEnd.add(new int[]{cr, cc});
            curr = parentEnd.get(curr);
        }
        
        shortestPath.addAll(fromStart);
        shortestPath.addAll(fromEnd);
    }

    // ============================================
    // --- 10 MAZE & PUZZLE GENERATORS ---
    // ============================================
    public static int[][] generatePattern(String pattern, int rows, int cols) {
        if (pattern.equalsIgnoreCase("kruskals")) {
            return generateKruskals(rows, cols);
        } else if (pattern.equalsIgnoreCase("prims")) {
            return generatePrims(rows, cols);
        } else if (pattern.equalsIgnoreCase("recursive-division")) {
            return generateRecursiveDivision(rows, cols);
        } else if (pattern.equalsIgnoreCase("binary-tree")) {
            return generateBinaryTree(rows, cols);
        } else if (pattern.equalsIgnoreCase("spiral")) {
            return generateSpiral(rows, cols);
        } else if (pattern.equalsIgnoreCase("double-spiral")) {
            return generateDoubleSpiral(rows, cols);
        } else if (pattern.equalsIgnoreCase("horizontal-blinds")) {
            return generateHorizontalBlinds(rows, cols);
        } else if (pattern.equalsIgnoreCase("diagonal-trap")) {
            return generateDiagonalTrap(rows, cols);
        } else if (pattern.equalsIgnoreCase("chamber-rooms")) {
            return generateChamberRooms(rows, cols);
        } else {
            return generateMaze(rows, cols);
        }
    }

    public static List<int[]> identifyFakeEnds(int[][] grid, int startR, int startC, int endR, int endC) {
        List<int[]> deadEnds = new ArrayList<>();
        int rows = grid.length;
        int cols = grid[0].length;
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};

        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < cols - 1; c++) {
                if (grid[r][c] == 0) {
                    if ((r == startR && c == startC) || (r == endR && c == endC)) continue;
                    
                    int wallsCount = 0;
                    for (int i = 0; i < 4; i++) {
                        int nr = r + dr[i];
                        int nc = c + dc[i];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols || grid[nr][nc] == 1) {
                            wallsCount++;
                        }
                    }
                    
                    if (wallsCount >= 3) {
                        deadEnds.add(new int[]{r, c});
                    }
                }
            }
        }

        Collections.shuffle(deadEnds);
        List<int[]> fakeEnds = new ArrayList<>();
        for (int i = 0; i < Math.min(3, deadEnds.size()); i++) {
            fakeEnds.add(deadEnds.get(i));
        }
        return fakeEnds;
    }

    public static void applyLoops(int[][] grid, double loopRate) {
        if (loopRate <= 0.0) return;
        int rows = grid.length;
        int cols = grid[0].length;
        Random rand = new Random();

        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < cols - 1; c++) {
                if (grid[r][c] == 1) {
                    boolean horizontalConnect = (grid[r][c - 1] == 0 && grid[r][c + 1] == 0);
                    boolean verticalConnect = (grid[r - 1][c] == 0 && grid[r + 1][c] == 0);

                    if ((horizontalConnect || verticalConnect) && rand.nextDouble() < loopRate) {
                        grid[r][c] = 0;
                    }
                }
            }
        }
    }

    // 1. Recursive Backtracker (DFS)
    public static int[][] generateMaze(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) Arrays.fill(grid[r], 1);
        carveDFS(grid, 1, 1);
        return grid;
    }

    private static void carveDFS(int[][] grid, int r, int c) {
        grid[r][c] = 0;
        int[][] dirs = {{-2, 0, -1, 0}, {0, 2, 0, 1}, {2, 0, 1, 0}, {0, -2, 0, -1}};
        List<int[]> dirList = Arrays.asList(dirs);
        Collections.shuffle(dirList);

        for (int[] dir : dirList) {
            int nr = r + dir[0];
            int nc = c + dir[1];
            int mr = r + dir[2];
            if (nr > 0 && nr < grid.length - 1 && nc > 0 && nc < grid[0].length - 1) {
                if (grid[nr][nc] == 1) {
                    grid[mr][dir[3] == 0 ? c : c + dir[3]] = 0;
                    carveDFS(grid, nr, nc);
                }
            }
        }
    }

    // 2. Kruskal's
    private static int[][] generateKruskals(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) Arrays.fill(grid[r], 1);

        int cellRows = rows / 2;
        int cellCols = cols / 2;
        DisjointSet ds = new DisjointSet(cellRows * cellCols);

        List<int[]> walls = new ArrayList<>();
        for (int r = 1; r < rows - 1; r += 2) {
            for (int c = 1; c < cols - 1; c += 2) {
                grid[r][c] = 0;
                int cellId = (r / 2) * cellCols + (c / 2);

                if (r + 2 < rows - 1) {
                    walls.add(new int[]{r + 1, c, cellId, cellId + cellCols});
                }
                if (c + 2 < cols - 1) {
                    walls.add(new int[]{r, c + 1, cellId, cellId + 1});
                }
            }
        }

        Collections.shuffle(walls);

        for (int[] wall : walls) {
            int wr = wall[0];
            int wc = wall[1];
            int c1 = wall[2];
            int c2 = wall[3];

            if (ds.find(c1) != ds.find(c2)) {
                ds.union(c1, c2);
                grid[wr][wc] = 0;
            }
        }

        return grid;
    }

    // 3. Prim's
    private static int[][] generatePrims(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) Arrays.fill(grid[r], 1);

        List<int[]> frontiers = new ArrayList<>();
        grid[1][1] = 0;

        addFrontiers(grid, 1, 1, frontiers);

        while (!frontiers.isEmpty()) {
            int randIdx = (int)(Math.random() * frontiers.size());
            int[] cell = frontiers.remove(randIdx);
            int r = cell[0];
            int c = cell[1];

            if (grid[r][c] == 1) {
                List<int[]> neighbors = new ArrayList<>();
                int[][] dirs = {{-2, 0, -1, 0}, {2, 0, 1, 0}, {0, -2, 0, -1}, {0, 2, 0, 1}};
                for (int[] d : dirs) {
                    int nr = r + d[0];
                    int nc = c + d[1];
                    if (nr > 0 && nr < rows - 1 && nc > 0 && nc < cols - 1 && grid[nr][nc] == 0) {
                        neighbors.add(d);
                    }
                }

                if (!neighbors.isEmpty()) {
                    int[] chosenDir = neighbors.get((int)(Math.random() * neighbors.size()));
                    grid[r][c] = 0;
                    grid[r + chosenDir[2]][c + chosenDir[3]] = 0;
                    addFrontiers(grid, r, c, frontiers);
                }
            }
        }

        return grid;
    }

    // 4. Recursive Division
    private static int[][] generateRecursiveDivision(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) ? 1 : 0;
            }
        }

        divide(grid, 1, rows - 2, 1, cols - 2);
        return grid;
    }

    private static void divide(int[][] grid, int minR, int maxR, int minC, int maxC) {
        int width = maxC - minC + 1;
        int height = maxR - minR + 1;

        if (width < 2 || height < 2) return;

        boolean horizontal = (width < height);
        if (width == height) {
            horizontal = Math.random() < 0.5;
        }

        Random rand = new Random();

        if (horizontal) {
            List<Integer> evenRows = new ArrayList<>();
            for (int r = minR + 1; r < maxR; r += 2) evenRows.add(r + 1);
            if (evenRows.isEmpty()) return;
            int wallR = evenRows.get(rand.nextInt(evenRows.size()));

            List<Integer> oddCols = new ArrayList<>();
            for (int c = minC; c <= maxC; c += 2) oddCols.add(c);
            int passageC = oddCols.get(rand.nextInt(oddCols.size()));

            for (int c = minC; c <= maxC; c++) {
                grid[wallR][c] = 1;
            }
            grid[wallR][passageC] = 0;

            divide(grid, minR, wallR - 1, minC, maxC);
            divide(grid, wallR + 1, maxR, minC, maxC);
        } else {
            List<Integer> evenCols = new ArrayList<>();
            for (int c = minC + 1; c < maxC; c += 2) evenCols.add(c + 1);
            if (evenCols.isEmpty()) return;
            int wallC = evenCols.get(rand.nextInt(evenCols.size()));

            List<Integer> oddRows = new ArrayList<>();
            for (int r = minR; r <= maxR; r += 2) oddRows.add(r);
            int passageR = oddRows.get(rand.nextInt(oddRows.size()));

            for (int r = minR; r <= maxR; r++) {
                grid[r][wallC] = 1;
            }
            grid[passageR][wallC] = 0;

            divide(grid, minR, maxR, minC, wallC - 1);
            divide(grid, minR, maxR, wallC + 1, maxC);
        }
    }

    // 5. Binary Tree
    private static int[][] generateBinaryTree(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) Arrays.fill(grid[r], 1);

        for (int r = 1; r < rows - 1; r += 2) {
            for (int c = 1; c < cols - 1; c += 2) {
                grid[r][c] = 0;

                List<int[]> choices = new ArrayList<>();
                if (r > 1) choices.add(new int[]{-1, 0});
                if (c > 1) choices.add(new int[]{0, -1});

                if (!choices.isEmpty()) {
                    int[] d = choices.get((int)(Math.random() * choices.size()));
                    grid[r + d[0]][c + d[1]] = 0;
                }
            }
        }
        return grid;
    }

    // 6. Spiral
    private static int[][] generateSpiral(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) ? 1 : 0;
            }
        }

        int minR = 2, maxR = rows - 3;
        int minC = 2, maxC = cols - 3;

        while (minR <= maxR && minC <= maxC) {
            for (int c = minC; c <= maxC; c++) grid[minR][c] = 1;
            for (int r = minR + 1; r <= maxR; r++) grid[r][maxC] = 1;
            for (int c = maxC - 1; c >= minC; c--) grid[maxR][c] = 1;
            for (int r = maxR - 1; r >= minR + 2; r--) grid[r][minC] = 1;

            if (minC + 1 <= maxC) grid[minR][minC + 1] = 0;
            if (minR + 1 <= maxR) grid[maxR - 1][minC] = 0;

            minR += 2;
            maxR -= 2;
            minC += 2;
            maxC -= 2;
        }

        return grid;
    }

    // 7. Double Spiral
    private static int[][] generateDoubleSpiral(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) ? 1 : 0;
            }
        }

        int minR = 2, maxR = rows - 3;
        int minC = 2, maxC = cols - 3;

        while (minR < maxR && minC < maxC) {
            for (int c = minC; c <= maxC - 2; c++) grid[minR][c] = 1;
            for (int r = minR; r <= maxR - 2; r++) grid[r][maxC - 2] = 1;

            for (int c = maxC; c >= minC + 2; c--) grid[maxR][c] = 1;
            for (int r = maxR; r >= minR + 2; r--) grid[r][minC + 2] = 1;

            grid[minR][maxC - 3] = 0;
            grid[maxR][minC + 3] = 0;

            minR += 3;
            maxR -= 3;
            minC += 3;
            maxC -= 3;
        }

        return grid;
    }

    // 8. Horizontal Blinds
    private static int[][] generateHorizontalBlinds(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) ? 1 : 0;
            }
        }

        boolean doorOnRight = true;
        for (int r = 2; r < rows - 2; r += 2) {
            for (int c = 1; c < cols - 1; c++) {
                grid[r][c] = 1;
            }
            if (doorOnRight) {
                grid[r][cols - 2] = 0;
                grid[r][cols - 3] = 0;
            } else {
                grid[r][1] = 0;
                grid[r][2] = 0;
            }
            doorOnRight = !doorOnRight;
        }

        return grid;
    }

    // 9. Diagonal Grid Trap
    private static int[][] generateDiagonalTrap(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) ? 1 : 0;
            }
        }

        for (int r = 2; r < rows - 2; r += 2) {
            for (int c = 2; c < cols - 2; c += 2) {
                if ((r + c) % 4 == 0) {
                    grid[r][c] = 1;
                    if (r + 1 < rows - 1) grid[r + 1][c] = 1;
                }
            }
        }
        
        return grid;
    }

    // 10. Chamber Rooms
    private static int[][] generateChamberRooms(int rows, int cols) {
        int[][] grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) ? 1 : 0;
            }
        }

        int r1 = rows / 3;
        int r2 = (2 * rows) / 3;
        int c1 = cols / 3;
        int c2 = (2 * cols) / 3;

        for (int c = 1; c < cols - 1; c++) {
            grid[r1][c] = 1;
            grid[r2][c] = 1;
        }

        for (int r = 1; r < rows - 1; r++) {
            grid[r][c1] = 1;
            grid[r][c2] = 1;
        }

        grid[r1][cols / 6] = 0;
        grid[r1][cols / 2] = 0;
        grid[r1][(5 * cols) / 6] = 0;
        
        grid[r2][cols / 6] = 0;
        grid[r2][cols / 2] = 0;
        grid[r2][(5 * cols) / 6] = 0;

        grid[rows / 6][c1] = 0;
        grid[rows / 2][c1] = 0;
        grid[(5 * rows) / 6][c1] = 0;
        
        grid[rows / 6][c2] = 0;
        grid[rows / 2][c2] = 0;
        grid[(5 * rows) / 6][c2] = 0;

        return grid;
    }

    // Helper method for Prim's
    private static void addFrontiers(int[][] grid, int r, int c, List<int[]> frontiers) {
        int[][] dirs = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}};
        for (int[] d : dirs) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (nr > 0 && nr < grid.length - 1 && nc > 0 && nc < grid[0].length - 1 && grid[nr][nc] == 1) {
                frontiers.add(new int[]{nr, nc});
            }
        }
    }

    // Helper class for Kruskal's
    private static class DisjointSet {
        int[] parent;
        DisjointSet(int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) parent[i] = i;
        }
        int find(int i) {
            if (parent[i] == i) return i;
            return parent[i] = find(parent[i]);
        }
        void union(int i, int j) {
            int rootI = find(i);
            int rootJ = find(j);
            if (rootI != rootJ) {
                parent[rootI] = rootJ;
            }
        }
    }
}
