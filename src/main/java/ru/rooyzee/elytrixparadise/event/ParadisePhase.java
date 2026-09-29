package ru.rooyzee.elytrixparadise.event;

public enum ParadisePhase {

    WAITING("waiting", "&#FFFFA0Подготовка", 300, false),
    ACTIVE("active", "&#208BFBАктивен", 900, true),
    CLIMAX("climax", "&#F8BEFBКульминация", 300, true),
    REWARD("reward", "&#A0FFA0Награда", 180, false),
    COOLDOWN("cooldown", "&#AAAAAAПерезарядка", 120, false);

    private final String id;
    private final String defaultDisplayName;
    private final int defaultDurationSeconds;
    private final boolean defaultPvp;

    ParadisePhase(String id, String defaultDisplayName, int defaultDurationSeconds, boolean defaultPvp) {
        this.id = id;
        this.defaultDisplayName = defaultDisplayName;
        this.defaultDurationSeconds = defaultDurationSeconds;
        this.defaultPvp = defaultPvp;
    }

    public String getId() {
        return id;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public int getDefaultDurationSeconds() {
        return defaultDurationSeconds;
    }

    public boolean isDefaultPvp() {
        return defaultPvp;
    }

    public static ParadisePhase fromId(String id) {
        if (id == null) return null;
        for (ParadisePhase phase : values()) {
            if (phase.id.equalsIgnoreCase(id) || phase.name().equalsIgnoreCase(id)) {
                return phase;
            }
        }
        return null;
    }

    public ParadisePhase next() {
        switch (this) {
            case WAITING:
                return ACTIVE;
            case ACTIVE:
                return CLIMAX;
            case CLIMAX:
                return REWARD;
            case REWARD:
                return COOLDOWN;
            case COOLDOWN:
            default:
                return WAITING;
        }
    }
}
