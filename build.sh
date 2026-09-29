#!/usr/bin/env bash
set -e

echo "=== Сборка ElytrixParadise ==="
export JAVA_HOME=${JAVA_HOME:-/home/user/jdk}
export PATH=$JAVA_HOME/bin:$PATH

BUILD_DIR=/tmp/elytrix_build
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"
mkdir -p target

CP="libs/spigot-api-shaded.jar:libs/worldedit-core-7.2.0.jar:libs/worldedit-bukkit-7.2.0.jar:libs/worldguard-bukkit-7.0.5-dist.jar:libs/DecentHolograms-2.8.16.jar:libs/PlaceholderAPI-2.12.3.jar"

echo "[1/3] Поиск исходных файлов ElytrixParadise..."
find src/main/java/ru/rooyzee/elytrixparadise -name "*.java" > "$BUILD_DIR/sources.txt"

echo "[2/3] Компиляция классов..."
javac -encoding UTF-8 -cp "$CP" -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"

echo "[3/3] Упаковка в ElytrixParadise.jar..."
cp src/main/resources/plugin.yml "$BUILD_DIR/classes/"
cp src/main/resources/config.yml "$BUILD_DIR/classes/"
cp src/main/resources/messages.yml "$BUILD_DIR/classes/"
jar -cvf ElytrixParadise.jar -C "$BUILD_DIR/classes" .
cp ElytrixParadise.jar target/ElytrixParadise.jar

echo "=== Сборка успешно завершена! Файл: ElytrixParadise.jar ==="
