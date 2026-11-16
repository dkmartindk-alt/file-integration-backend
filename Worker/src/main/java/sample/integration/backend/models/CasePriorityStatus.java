package sample.integration.backend.models;

public enum CasePriorityStatus {
    HIGH(10),
    LOW(0);

    private final int priorityValue;

    CasePriorityStatus(int priorityValue) {
        this.priorityValue = priorityValue;
    }

    public int getValue() {
        return priorityValue;
    }
}
