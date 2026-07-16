package com.mazesolver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class MazeSolverApp {

    private static final int PORT = 8080;

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", new StaticHandler());
            server.createContext("/api/generate", new GenerateHandler());
            server.createContext("/api/solve", new SolveHandler());
            
            server.setExecutor(null); // default executor
            System.out.println("Maze Solver server started at http://localhost:" + PORT);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Class to hold parsed solve request parameters
    static class SolveRequest {
        int[][] grid;
        int startR, startC;
        int endR, endC;
        List<int[]> fakeEnds = new ArrayList<>();
        String algorithm;
    }

    // Custom lightweight JSON parser to keep application dependency-free
    private static SolveRequest parseSolveRequest(String json) {
        SolveRequest req = new SolveRequest();

        // 1. Parse algorithm
        int algoIdx = json.indexOf("\"algorithm\"");
        if (algoIdx != -1) {
            int colonIdx = json.indexOf(":", algoIdx);
            int quoteStart = json.indexOf("\"", colonIdx);
            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            req.algorithm = json.substring(quoteStart + 1, quoteEnd);
        } else {
            req.algorithm = "backtracking";
        }

        // 2. Parse start coord [r, c]
        int startIdx = json.indexOf("\"start\"");
        if (startIdx != -1) {
            int bracketStart = json.indexOf("[", startIdx);
            int bracketEnd = json.indexOf("]", bracketStart);
            String[] parts = json.substring(bracketStart + 1, bracketEnd).split(",");
            req.startR = Integer.parseInt(parts[0].trim());
            req.startC = Integer.parseInt(parts[1].trim());
        }

        // 3. Parse end coord [r, c]
        int endIdx = json.indexOf("\"end\"");
        if (endIdx != -1) {
            int bracketStart = json.indexOf("[", endIdx);
            int bracketEnd = json.indexOf("]", bracketStart);
            String[] parts = json.substring(bracketStart + 1, bracketEnd).split(",");
            req.endR = Integer.parseInt(parts[0].trim());
            req.endC = Integer.parseInt(parts[1].trim());
        }

        // 4. Parse fakeEnds [[r,c],[r,c]]
        int fakeIdx = json.indexOf("\"fakeEnds\"");
        if (fakeIdx != -1) {
            int bracketStart = json.indexOf("[[", fakeIdx);
            if (bracketStart != -1) {
                int openBrackets = 0;
                int bracketEnd = -1;
                for (int i = bracketStart; i < json.length(); i++) {
                    char ch = json.charAt(i);
                    if (ch == '[') {
                        openBrackets++;
                    } else if (ch == ']') {
                        openBrackets--;
                        if (openBrackets == 0) {
                            bracketEnd = i + 1;
                            break;
                        }
                    }
                }

                if (bracketEnd != -1) {
                    String fakeStr = json.substring(bracketStart, bracketEnd);
                    String inner = fakeStr.substring(1, fakeStr.length() - 1);
                    if (!inner.trim().isEmpty()) {
                        String[] pairs = inner.split("\\],\\s*\\[");
                        for (String pair : pairs) {
                            String clean = pair.replace("[", "").replace("]", "");
                            String[] parts = clean.split(",");
                            if (parts.length == 2) {
                                try {
                                    req.fakeEnds.add(new int[]{
                                        Integer.parseInt(parts[0].trim()),
                                        Integer.parseInt(parts[1].trim())
                                    });
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }
        }

        // 5. Parse grid 2D array [[...],[...]]
        int gridIdx = json.indexOf("\"grid\"");
        if (gridIdx != -1) {
            int gridStart = json.indexOf("[[", gridIdx);
            int openBrackets = 0;
            int gridEnd = -1;
            for (int i = gridStart; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '[') {
                    openBrackets++;
                } else if (ch == ']') {
                    openBrackets--;
                    if (openBrackets == 0) {
                        gridEnd = i + 1;
                        break;
                    }
                }
            }

            if (gridEnd != -1) {
                String gridStr = json.substring(gridStart, gridEnd);
                String inner = gridStr.substring(1, gridStr.length() - 1);
                
                String[] rowsStr = inner.split("\\],\\s*\\[");
                int numRows = rowsStr.length;

                rowsStr[0] = rowsStr[0].replace("[", "");
                rowsStr[numRows - 1] = rowsStr[numRows - 1].replace("]", "");

                int numCols = rowsStr[0].split(",").length;
                req.grid = new int[numRows][numCols];

                for (int r = 0; r < numRows; r++) {
                    String[] colsStr = rowsStr[r].split(",");
                    for (int c = 0; c < numCols; c++) {
                        req.grid[r][c] = Integer.parseInt(colsStr[c].trim());
                    }
                }
            }
        }

        return req;
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            File localFile = new File("src/main/resources/web" + path);
            byte[] content;
            String contentType = "text/plain";

            if (localFile.exists() && !localFile.isDirectory()) {
                content = Files.readAllBytes(localFile.toPath());
            } else {
                InputStream is = MazeSolverApp.class.getResourceAsStream("/web" + path);
                if (is == null) {
                    String response = "404 Not Found";
                    exchange.sendResponseHeaders(404, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }
                content = is.readAllBytes();
                is.close();
            }

            if (path.endsWith(".html")) {
                contentType = "text/html; charset=utf-8";
            } else if (path.endsWith(".css")) {
                contentType = "text/css; charset=utf-8";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript; charset=utf-8";
            } else if (path.endsWith(".png")) {
                contentType = "image/png";
            } else if (path.endsWith(".svg")) {
                contentType = "image/svg+xml";
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();
        }
    }

    // Handler to generate random mazes with loop and fake ends postprocessing
    static class GenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET") && !exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            int rows = 21;
            int cols = 21;
            String pattern = "dfs";
            double loopRate = 0.0;
            
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        try {
                            if (pair[0].equals("rows")) {
                                rows = Integer.parseInt(pair[1]);
                            } else if (pair[0].equals("cols")) {
                                cols = Integer.parseInt(pair[1]);
                            } else if (pair[0].equals("pattern")) {
                                pattern = pair[1];
                            } else if (pair[0].equals("loopRate")) {
                                loopRate = Double.parseDouble(pair[1]);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // Ensure dimensions are odd to allow walls and paths placement
            if (rows % 2 == 0) rows++;
            if (cols % 2 == 0) cols++;

            rows = Math.min(Math.max(rows, 5), 51);
            cols = Math.min(Math.max(cols, 5), 51);

            int[][] maze = MazeSolver.generatePattern(pattern, rows, cols);
            
            // Postprocess loops and crossovers
            MazeSolver.applyLoops(maze, loopRate);

            // Identify natural dead ends to label as Fake Targets (Traps)
            int startR = 1, startC = 1;
            int endR = rows - 2, endC = cols - 2;
            List<int[]> fakeEnds = MazeSolver.identifyFakeEnds(maze, startR, startC, endR, endC);

            // Serialize 2D array and fake ends list to JSON
            StringBuilder json = new StringBuilder();
            json.append("{\"grid\":[");
            for (int r = 0; r < rows; r++) {
                json.append("[");
                for (int c = 0; c < cols; c++) {
                    json.append(maze[r][c]);
                    if (c < cols - 1) json.append(",");
                }
                json.append("]");
                if (r < rows - 1) json.append(",");
            }
            json.append("],\"fakeEnds\":[");
            for (int i = 0; i < fakeEnds.size(); i++) {
                int[] fe = fakeEnds.get(i);
                json.append("[").append(fe[0]).append(",").append(fe[1]).append("]");
                if (i < fakeEnds.size() - 1) json.append(",");
            }
            json.append("]}");

            byte[] content = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();
        }
    }

    // Handler to solve mazes
    static class SolveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream is = exchange.getRequestBody();
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            try {
                SolveRequest req = parseSolveRequest(json);
                MazeSolver.SolveResult result = MazeSolver.solve(req.grid, req.startR, req.startC, req.endR, req.endC, req.fakeEnds, req.algorithm);

                String response = result.toJson();
                byte[] content = response.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, content.length);
                OutputStream os = exchange.getResponseBody();
                os.write(content);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String response = "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
                byte[] content = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(400, content.length);
                OutputStream os = exchange.getResponseBody();
                os.write(content);
                os.close();
            }
        }
    }
}
