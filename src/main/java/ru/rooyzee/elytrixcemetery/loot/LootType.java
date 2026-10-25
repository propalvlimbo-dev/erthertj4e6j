package ru.rooyzee.elytrixcemetery.loot;

public enum LootType {
    GRAVES("Сундуки Могил", "graves.yml"),
    GHOSTS("Призраки Кладбища", "ghosts.yml");

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
