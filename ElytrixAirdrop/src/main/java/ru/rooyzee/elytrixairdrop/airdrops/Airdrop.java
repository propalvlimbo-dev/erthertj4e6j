package ru.rooyzee.elytrixairdrop.airdrops;

public enum Airdrop {
    PEACEFUL("peaceful", false),
    FIRE("fire", true),
    SKY("sky", true);

    private final String id;
    private final boolean pvpAllowed;

    Airdrop(String id, boolean pvpAllowed) {
        this.id = id;
        this.pvpAllowed = pvpAllowed;
    }

    public String getId() { return id; }
    public boolean isPvpAllowed() { return pvpAllowed; }

    public static Airdrop fromId(String id) {
        for (Airdrop a : values()) {
            if (a.id.equalsIgnoreCase(id)) return a;
        }
        return null;
    }
}