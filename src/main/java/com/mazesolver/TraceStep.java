package com.mazesolver;

public class TraceStep {
    public String type; // "visit", "backtrack", "solution"
    public int r;
    public int c;
    public String description;

    public TraceStep(String type, int r, int c, String description) {
        this.type = type;
        this.r = r;
        this.c = c;
        this.description = description;
    }

    public String toJson() {
        return String.format("{\"type\":\"%s\",\"r\":%d,\"c\":%d,\"description\":\"%s\"}",
            type, r, c, description.replace("\"", "\\\""));
    }
}
