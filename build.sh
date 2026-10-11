#!/usr/bin/env bash
set -e

if [ ! -d "/home/user/erthertj4e6j/.jdk/bin" ]; then
    echo "Загрузка JDK..."
    git clone --depth 1 --filter=blob:none --sparse https://github.com/mingjiewang/qap.git /tmp/qap
    cd /tmp/qap
    git sparse-checkout set bin/3rdPartyTools/jdk
    cp -r /tmp/qap/bin/3rdPartyTools/jdk /home/user/erthertj4e6j/.jdk
    chmod -R +x /home/user/erthertj4e6j/.jdk/bin /home/user/erthertj4e6j/.jdk/jre/bin
    rm -rf /tmp/qap
    cd /home/user/erthertj4e6j
fi

export JAVA_HOME="/home/user/erthertj4e6j/.jdk"
export PATH=$JAVA_HOME/bin:$PATH

if [ ! -f "/tmp/stubs.jar" ]; then
    if [ -f "/tmp/gen_stubs.sh" ]; then
        /tmp/gen_stubs.sh
    fi
fi

CP="libs/spigot-api-shaded.jar:libs/worldedit-core-7.2.0.jar:libs/worldedit-bukkit-7.2.0.jar:libs/FastAsyncWorldEdit-Bukkit-2.4.10.jar:libs/worldguard-bukkit-7.0.5-dist.jar:libs/DecentHolograms-2.8.16.jar:libs/PlaceholderAPI-2.12.3.jar"

echo "=== [1/4] Сборка ElytrixParadise ==="
BUILD_DIR=/tmp/elytrix_build
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"
mkdir -p target

find src/main/java/ru/rooyzee/elytrixparadise -name "*.java" > "$BUILD_DIR/sources.txt"
javac -encoding UTF-8 -cp "$CP" -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
cp src/main/resources/plugin.yml "$BUILD_DIR/classes/"
cp src/main/resources/config.yml "$BUILD_DIR/classes/"
cp src/main/resources/messages.yml "$BUILD_DIR/classes/"
jar -cf ElytrixParadise.jar -C "$BUILD_DIR/classes" .
cp ElytrixParadise.jar target/ElytrixParadise.jar

echo "=== [2/4] Сборка ElytrixAirdrop ==="
BUILD_AIRDROP=/tmp/airdrop_build
rm -rf "$BUILD_AIRDROP"
mkdir -p "$BUILD_AIRDROP/classes"
find ElytrixAirdrop/src/main/java -name "*.java" > "$BUILD_AIRDROP/sources.txt"
javac -encoding UTF-8 -cp "$CP" -d "$BUILD_AIRDROP/classes" @"$BUILD_AIRDROP/sources.txt"
cp ElytrixAirdrop/src/main/resources/plugin.yml "$BUILD_AIRDROP/classes/"
cp ElytrixAirdrop/src/main/resources/config.yml "$BUILD_AIRDROP/classes/"
cp ElytrixAirdrop/src/main/resources/messages.yml "$BUILD_AIRDROP/classes/"
jar -cf ElytrixAirdrop.jar -C "$BUILD_AIRDROP/classes" .

echo "=== [3/4] Сборка ElytrixSchalkerPvP ==="
BUILD_SCHALKER=/tmp/schalker_build
rm -rf "$BUILD_SCHALKER"
mkdir -p "$BUILD_SCHALKER/classes"
find ElytrixSchalkerPvP/src/main/java -name "*.java" > "$BUILD_SCHALKER/sources.txt"
javac -encoding UTF-8 -cp "$CP" -d "$BUILD_SCHALKER/classes" @"$BUILD_SCHALKER/sources.txt"
cp ElytrixSchalkerPvP/src/main/resources/plugin.yml "$BUILD_SCHALKER/classes/"
cp ElytrixSchalkerPvP/src/main/resources/config.yml "$BUILD_SCHALKER/classes/"
mkdir -p ElytrixSchalkerPvP/target
jar -cf ElytrixSchalkerPvP/target/ElytrixSchalkerPvP.jar -C "$BUILD_SCHALKER/classes" .
cp ElytrixSchalkerPvP/target/ElytrixSchalkerPvP.jar ElytrixSchalkerPvP.jar

echo "=== [4/4] Сборка ElytrixTrader ==="
BUILD_TRADER=/tmp/trader_build
rm -rf "$BUILD_TRADER"
mkdir -p "$BUILD_TRADER/classes"
find ElytrixTrader/src/main/java -name "*.java" > "$BUILD_TRADER/sources.txt"
javac -encoding UTF-8 -cp "$CP:/tmp/stubs.jar" -d "$BUILD_TRADER/classes" @"$BUILD_TRADER/sources.txt"
cp -r ElytrixTrader/src/main/resources/* "$BUILD_TRADER/classes/"
mkdir -p ElytrixTrader/target
jar -cf ElytrixTrader/target/ElytrixTrader.jar -C "$BUILD_TRADER/classes" .
cp ElytrixTrader/target/ElytrixTrader.jar ElytrixTrader.jar

echo "=== ВСЕ ПЛАГИНЫ УСПЕШНО СОБРАНЫ! ==="
ls -lh ElytrixParadise.jar ElytrixAirdrop.jar ElytrixSchalkerPvP.jar ElytrixTrader.jar
