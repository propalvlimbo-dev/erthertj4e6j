package ru.rooyzee.elytrixparadise.sphere;

public enum SphereState {
    INTACT,           // Сфера подвешена на цепях, игроки ломают цепи
    FALLING,          // Анимация падения сферы вниз на алтарь
    FALLEN_MINING,    // Сфера лежит на алтаре, игроки ломают её и получают лут (без взрыва)
    ASCENDING,        // Сфера плавно поднимается обратно вверх
    RESTORING_CHAINS, // Цепи плавно соединяются со сферой
    COOLDOWN          // Перезарядка до следующего ивента сферы
}
