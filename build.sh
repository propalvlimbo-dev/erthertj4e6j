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

mkdir -p /tmp/stubs/net/milkbowl/vault/economy
mkdir -p /tmp/stubs/net/kyori/adventure/text/serializer/legacy
mkdir -p /tmp/stubs/net/kyori/adventure/text/serializer/gson
mkdir -p /tmp/stubs/io/netty/channel
mkdir -p /tmp/stubs/dev/by1337/virtualentity/api/entity
mkdir -p /tmp/stubs/dev/by1337/virtualentity/api/tracker
mkdir -p /tmp/stubs/dev/by1337/virtualentity/api/virtual/decoration
mkdir -p /tmp/stubs/dev/by1337/virtualentity/api/virtual/player
mkdir -p /tmp/stubs/org/by1337/blib/geom

cat << 'STUB_EOF' > /tmp/stubs/net/milkbowl/vault/economy/Economy.java
package net.milkbowl.vault.economy;
import org.bukkit.OfflinePlayer;
public interface Economy {
    boolean isEnabled();
    String getName();
    boolean hasBankSupport();
    int fractionalDigits();
    String format(double amount);
    String currencyNamePlural();
    String currencyNameSingular();
    boolean hasAccount(String playerName);
    boolean hasAccount(OfflinePlayer player);
    double getBalance(String playerName);
    double getBalance(OfflinePlayer player);
    boolean has(String playerName, double amount);
    boolean has(OfflinePlayer player, double amount);
    EconomyResponse withdrawPlayer(String playerName, double amount);
    EconomyResponse withdrawPlayer(OfflinePlayer player, double amount);
    EconomyResponse depositPlayer(String playerName, double amount);
    EconomyResponse depositPlayer(OfflinePlayer player, double amount);
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/net/milkbowl/vault/economy/EconomyResponse.java
package net.milkbowl.vault.economy;
public class EconomyResponse {
    public enum ResponseType { SUCCESS, FAILURE, NOT_IMPLEMENTED }
    public final double amount;
    public final double balance;
    public final ResponseType type;
    public final String errorMessage;
    public EconomyResponse(double amount, double balance, ResponseType type, String errorMessage) {
        this.amount = amount;
        this.balance = balance;
        this.type = type;
        this.errorMessage = errorMessage;
    }
    public boolean transactionSuccess() { return type == ResponseType.SUCCESS; }
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/net/kyori/adventure/text/Component.java
package net.kyori.adventure.text;
public interface Component {
    static TextComponent empty() { return new TextComponentImpl(""); }
    static TextComponent text(String content) { return new TextComponentImpl(content); }
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/net/kyori/adventure/text/TextComponent.java
package net.kyori.adventure.text;
public interface TextComponent extends Component {
    String content();
}
class TextComponentImpl implements TextComponent {
    private final String content;
    public TextComponentImpl(String content) { this.content = content != null ? content : ""; }
    public String content() { return content; }
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/net/kyori/adventure/text/serializer/gson/GsonComponentSerializer.java
package net.kyori.adventure.text.serializer.gson;
import net.kyori.adventure.text.Component;
public interface GsonComponentSerializer {
    static GsonComponentSerializer gson() { return new GsonComponentSerializerImpl(); }
    Component deserialize(String input);
    String serialize(Component component);
}
class GsonComponentSerializerImpl implements GsonComponentSerializer {
    public Component deserialize(String input) { return Component.text(input); }
    public String serialize(Component component) { return ""; }
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/net/kyori/adventure/text/serializer/legacy/LegacyComponentSerializer.java
package net.kyori.adventure.text.serializer.legacy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
public interface LegacyComponentSerializer {
    static LegacyComponentSerializer legacySection() { return new LegacyComponentSerializerImpl(); }
    static LegacyComponentSerializer legacyAmpersand() { return new LegacyComponentSerializerImpl(); }
    TextComponent deserialize(String input);
    String serialize(Component component);
}
class LegacyComponentSerializerImpl implements LegacyComponentSerializer {
    public TextComponent deserialize(String input) { return Component.text(input); }
    public String serialize(Component component) { return ""; }
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/io/netty/channel/Channel.java
package io.netty.channel;
public interface Channel {
    ChannelPipeline pipeline();
    boolean isOpen();
    boolean isActive();
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/io/netty/channel/ChannelPipeline.java
package io.netty.channel;
public interface ChannelPipeline {
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);
    ChannelPipeline addLast(String name, ChannelHandler handler);
    ChannelPipeline addLast(ChannelHandler... handlers);
    ChannelPipeline remove(String name);
    ChannelHandler get(String name);
    ChannelHandler remove(ChannelHandler handler);
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/io/netty/channel/ChannelHandler.java
package io.netty.channel;
public interface ChannelHandler {}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/io/netty/channel/ChannelHandlerContext.java
package io.netty.channel;
public interface ChannelHandlerContext {
    void fireChannelRead(Object msg);
    void fireExceptionCaught(Throwable cause);
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/io/netty/channel/ChannelInboundHandlerAdapter.java
package io.netty.channel;
public class ChannelInboundHandlerAdapter implements ChannelHandler {
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception { ctx.fireChannelRead(msg); }
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception { ctx.fireExceptionCaught(cause); }
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/org/by1337/blib/geom/Vec3d.java
package org.by1337.blib.geom;
import org.bukkit.Location;
public class Vec3d {
    private double x, y, z;
    public Vec3d(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public Vec3d(Location loc) { this.x = loc.getX(); this.y = loc.getY(); this.z = loc.getZ(); }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/dev/by1337/virtualentity/api/virtual/VirtualEntity.java
package dev.by1337.virtualentity.api.virtual;
import org.by1337.blib.geom.Vec3d;
import org.bukkit.entity.Player;
public interface VirtualEntity {
    int getId();
    void setPos(Vec3d pos);
    void setPitch(float pitch);
    void setYaw(float yaw);
    void setCustomNameVisible(boolean visible);
    void setInvisible(boolean invisible);
    void setSilent(boolean silent);
    void setNoGravity(boolean noGravity);
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/dev/by1337/virtualentity/api/entity/EntityAnimation.java
package dev.by1337.virtualentity.api.entity;
public enum EntityAnimation {
    SWING_MAIN_ARM,
    TAKE_DAMAGE,
    LEAVE_BED,
    SWING_OFFHAND,
    CRITICAL_HIT,
    MAGIC_CRITICAL_HIT
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/dev/by1337/virtualentity/api/entity/EquipmentSlot.java
package dev.by1337.virtualentity.api.entity;
public enum EquipmentSlot {
    MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/dev/by1337/virtualentity/api/virtual/player/VirtualPlayer.java
package dev.by1337.virtualentity.api.virtual.player;
import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.by1337.blib.geom.Vec3d;
public interface VirtualPlayer extends VirtualEntity {
    static VirtualPlayer create() { return null; }
    void setName(String name);
    void setDisplayName(Component displayName);
    void setTexture(String value, String signature);
    void setEquipment(EquipmentSlot slot, ItemStack item);
    void playAnimation(EntityAnimation animation);
    void lookAt(Vec3d pos);
    void respawn();
    void setListed(boolean listed);
    void hideFromTab();
    void setLatency(int latency);
    void setGameMode(GameMode gameMode);
    void setPlayerModeCustomisation(byte flags);
    void sendRemovePlayerPacket(Player player);
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/dev/by1337/virtualentity/api/virtual/decoration/VirtualArmorStand.java
package dev.by1337.virtualentity.api.virtual.decoration;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import net.kyori.adventure.text.Component;
import org.by1337.blib.geom.Vec3d;
public interface VirtualArmorStand extends VirtualEntity {
    static VirtualArmorStand create() { return null; }
    void setCustomName(Component name);
    void setSmall(boolean small);
    void setMarker(boolean marker);
    void setNoBasePlate(boolean noBasePlate);
    void setArms(boolean arms);
    void setHeadPose(Vec3d pose);
}
STUB_EOF

cat << 'STUB_EOF' > /tmp/stubs/dev/by1337/virtualentity/api/tracker/PlayerTracker.java
package dev.by1337.virtualentity.api.tracker;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import org.bukkit.World;
import org.by1337.blib.geom.Vec3d;
import java.util.List;
public class PlayerTracker {
    public PlayerTracker(World world, List<VirtualEntity> entities, Vec3d center, int radius) {}
    public void tick() {}
    public void removeAll() {}
}
STUB_EOF

rm -rf /tmp/stubs_classes
mkdir -p /tmp/stubs_classes
find /tmp/stubs -name "*.java" > /tmp/stubs_sources.txt
javac -d /tmp/stubs_classes -cp "libs/spigot-api-shaded.jar" @/tmp/stubs_sources.txt
jar cf /tmp/stubs.jar -C /tmp/stubs_classes .

CP="libs/spigot-api-shaded.jar:libs/worldedit-core-7.2.0.jar:libs/worldedit-bukkit-7.2.0.jar:libs/FastAsyncWorldEdit-Bukkit-2.4.10.jar:libs/worldguard-bukkit-7.0.5-dist.jar:libs/DecentHolograms-2.8.16.jar:libs/PlaceholderAPI-2.12.3.jar"

echo "=== [1/4] Сборка ElytrixCemetery ==="
BUILD_DIR=/tmp/cemetery_build
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"
mkdir -p target

find src/main/java/ru/rooyzee/elytrixcemetery -name "*.java" > "$BUILD_DIR/sources.txt"
javac -encoding UTF-8 -cp "$CP" -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
cp src/main/resources/plugin.yml "$BUILD_DIR/classes/"
cp src/main/resources/config.yml "$BUILD_DIR/classes/"
cp src/main/resources/messages.yml "$BUILD_DIR/classes/"
jar -cf ElytrixCemetery.jar -C "$BUILD_DIR/classes" .
cp ElytrixCemetery.jar target/ElytrixCemetery.jar

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
ls -lh ElytrixCemetery.jar ElytrixAirdrop.jar ElytrixSchalkerPvP.jar ElytrixTrader.jar
