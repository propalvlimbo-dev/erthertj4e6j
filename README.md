# ☁ ElytrixParadise

Плагин для Minecraft (Spigot / Paper 1.16.5 - 1.20+), создающий спавн райского острова из схематики на координатах **X=0, Y=130, Z=0** в мире **world**.

---

## 📁 Куда положить схематику?

Поместите ваш файл схематики по пути:
```text
plugins/ElytrixParadise/schematics/paradise.schem
```
*(Папка `schematics/` создаётся автоматически. Если у вас файл с расширением `.schematic` или другой версией формата, плагин автоматически определит и прочитает формат благодаря встроенному механизму fallback)*.

---

## ⚙️ Возможности

1. **Автоматический спавн схематики**:
   - При старте сервера плагин автоматически вставляет схематику на координаты **X=0, Y=130, Z=0** (настраивается в `config.yml`).
   - Если файл схематики ещё не помещён в папку, плагин создаёт безопасную платформу из облаков (кварц, стекло, светокамень), чтобы игроки не падали.

2. **Универсальная поддержка форматов схематик**:
   - Автоматическое распознавание современных форматов Sponge `.schem` (DataVersion 1.13+) и классических MCEdit `.schematic` (1.12.2 и ниже).
   - Защита от ошибок версии формата (`Unsupported schematic version: -1`) с автоматическим перебором доступных парсеров WorldEdit / FastAsyncWorldEdit.

3. **Команды управления**:
   - Телепортация на координаты спавна.
   - Принудительная перезагрузка и повторная вставка схематики администратором прямо из игры.

---

## 📜 Команды и права

| Команда | Описание | Право |
|---|---|---|
| `/ep help` | Список всех команд плагина | `elytrixparadise.use` (default: true) |
| `/ep tp` | Телепортация на райские облака (X=0, Y=132, Z=0) | `elytrixparadise.tp` (default: true) |
| `/ep info` | Координаты и имя файла схематики | `elytrixparadise.use` (default: true) |
| `/ep schem` | Инструкция по установке схематики | `elytrixparadise.use` (default: true) |
| `/ep reload` | Перезагрузить `config.yml` и `messages.yml` | `elytrixparadise.admin` (default: op) |
| `/ep paste` | Принудительно вставить схематику на X=0, Y=130, Z=0 | `elytrixparadise.admin` (default: op) |
| `/ep setcenter` | Установить текущую позицию центром Рая | `elytrixparadise.admin` (default: op) |
| `/ep setspawn` | Установить точку спавна телепортации `/ep tp` | `elytrixparadise.admin` (default: op) |

*Алиасы команд:* `/elytrixparadise`, `/paradise`, `/ep`

---

## 📥 Ссылка на скачивание

- **Готовый файл плагина:** [ElytrixParadise.jar](https://github.com/propalvlimbo-dev/erthertj4e6j/raw/arena/01a0e85a-erthertj4e6j/ElytrixParadise.jar)
