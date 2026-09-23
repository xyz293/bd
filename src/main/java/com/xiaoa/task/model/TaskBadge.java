package com.xiaoa.task.model;

public class TaskBadge {

    private final String code;
    private final String name;
    private final String description;
    private final boolean achieved;
    private final double value;
    private final double threshold;

    public TaskBadge(String code, String name, String description, boolean achieved,
                     double value, double threshold) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.achieved = achieved;
        this.value = value;
        this.threshold = threshold;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isAchieved() { return achieved; }
    public double getValue() { return value; }
    public double getThreshold() { return threshold; }
}
