package ru.rooyzee.elytrixparadise.loot;

public enum LootType {
    SHARDS("Осколки Рая", "shards.yml"),
    SPHERE("Сердце Рая", "sphere.yml");

    private final String title;
    private final String fileName;

    LootType(String title, String fileName) {
        this.title = title;
        this.fileName = fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }
}
