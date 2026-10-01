package ru.rooyzee.elytrixtrader.skin;

public class SkinProfile {

    private final String name;
    private final String value;
    private final String signature;
    private final long requestedAt;

    public SkinProfile(String name, String value, String signature) {
        this.name = name;
        this.value = value;
        this.signature = signature;
        this.requestedAt = System.currentTimeMillis();
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public String signature() {
        return signature;
    }

    public long requestedAt() {
        return requestedAt;
    }

    public boolean isValid() {
        return value != null && !value.isEmpty();
    }
}
