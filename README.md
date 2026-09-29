# ☁ ElytrixParadise

Плагин для Minecraft (Spigot / Paper 1.16.5 - 1.20+), создающий **всегда активный райский ивент на облаках** на координатах **X=0, Y=60, Z=0** в мире **world**.

---

## 📁 Куда класть схематику?

Поместите ваш файл схематики (`.schem` или `.schematic`) по следующему пути:
```
plugins/ElytrixParadise/schematics/paradise.schem
```
*(Если папки нет, она создаётся автоматически при первом запуске плагина. Имя файла можно изменить в `config.yml` в секции `schematic.file`)*.

---

## ⚙️ Основные возможности

1. **Автоматический спавн схематики**:
   - При старте сервера плагин автоматически находит файл `plugins/ElytrixParadise/schematics/paradise.schem` и вставляет его на координаты **X=0, Y=60, Z=0** (настраивается в `config.yml`).
   - Если схематика ещё не загружена, плагин создаёт стартовую облачную платформу из кварца, стекла и золотого алтаря.

2. **Всегда активный ивент (Event Loop)**:
   - Ивент работает непрерывно циклическими фазами:
     - `waiting` (Подготовка)
     - `active` (Активная фаза)
     - `climax` (Кульминация)
     - `reward` (Раздача наград)
     - `cooldown` (Перезарядка)
   - Длительность каждой фазы, отображаемые имена и статус PvP настраиваются в `config.yml`.
   - В следующем сообщении мы подключим конкретную механику ивента к этой готовой архитектуре!

3. **Защита региона (WorldGuard)**:
   - Автоматически создаётся регион `elytrix_paradise` с радиусом вокруг (0, Y, 0).
   - Запрет разрушения и установки блоков (кроме администраторов).
   - Защита от падений и спасение из бездны (void-save).

4. **Голограммы (DecentHolograms)**:
   - Автоматическая динамическая голограмма над центром Рая с таймером, текущей фазой и количеством игроков.

5. **Визуальные эффекты & BossBar**:
   - Облачные частицы вокруг райского острова.
   - BossBar и Actionbar для игроков в зоне Рая.

6. **PlaceholderAPI**:
   - `%elytrixparadise_status%` — статус и время
   - `%elytrixparadise_phase%` — название текущей фазы
   - `%elytrixparadise_time%` — оставшееся время (MM:SS)
   - `%elytrixparadise_time_russian%` — время на русском (например, "5 мин 30 сек")
   - `%elytrixparadise_players%` — количество игроков в Раю
   - `%elytrixparadise_x%`, `%elytrixparadise_y%`, `%elytrixparadise_z%` — координаты

---

## 📜 Команды и права

| Команда | Описание | Право |
|---|---|---|
| `/ep help` | Список доступных команд | `elytrixparadise.use` (default: true) |
| `/ep tp` | Телепортация в Рай на облака | `elytrixparadise.tp` (default: true) |
| `/ep info` | Подробная информация об ивенте | `elytrixparadise.use` (default: true) |
| `/ep schem` | Информация о пути к схематике | `elytrixparadise.use` (default: true) |
| `/ep reload` | Перезагрузка конфигурации и сообщений | `elytrixparadise.admin` (default: op) |
| `/ep paste` | Принудительно вставить схематику на (0, Y, 0) | `elytrixparadise.admin` (default: op) |
| `/ep start [фаза]` | Запустить ивент или принудительно переключить фазу | `elytrixparadise.admin` (default: op) |
| `/ep stop` | Приостановить ивент | `elytrixparadise.admin` (default: op) |
| `/ep setcenter` | Установить центр Рая на вашу позицию | `elytrixparadise.admin` (default: op) |
| `/ep setspawn` | Установить точку спавна телепортации | `elytrixparadise.admin` (default: op) |

*Алиасы команд:* `/elytrixparadise`, `/paradise`, `/ep`

---

## 📦 Сборка плагина

Готовый скомпилированный файл: **`ElytrixParadise.jar`** в корне репозитория.
