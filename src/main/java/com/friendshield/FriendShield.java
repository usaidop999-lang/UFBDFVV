package com.friendshield;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Friend Shield - independent client-side mod (own code, not derived from any other mod).
 * - Protected-player list: attacks on listed players are cancelled client-side.
 * - Compass: action-bar arrow pointing to the nearest NON-protected player,
 *   with the count of protected players nearby shown in green.
 */
public final class FriendShield implements ClientModInitializer {
    public static final String MOD_ID = "friendshield";
    private static final int[] RANGES = {16, 32, 64, 128, 256};
    private static final String[] ARROWS = {
        "\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"
    };

    private final Set<String> protectedNames = new LinkedHashSet<>();
    private Path configPath;
    private boolean enabled = true;
    private int rangeIndex = 2;
    private int ticks = 0;

    private KeyMapping protectKey;
    private KeyMapping toggleKey;
    private KeyMapping rangeKey;

    @Override
    public void onInitializeClient() {
        configPath = Minecraft.getInstance().gameDirectory.toPath().resolve("config/friendshield.properties");
        load();

        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        protectKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key." + MOD_ID + ".protect", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key." + MOD_ID + ".toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_R, category));
        rangeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key." + MOD_ID + ".range", InputConstants.Type.KEYSYM, InputConstants.KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && entity instanceof Player target && isProtected(target)) {
                Minecraft mc = Minecraft.getInstance();
                message(mc, Component.translatable("friendshield.blocked", target.getName().getString())
                    .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private void onTick(Minecraft mc) {
        // Always drain key clicks so they never pile up.
        boolean protect = false, toggle = false, range = false;
        while (protectKey.consumeClick()) protect = true;
        while (toggleKey.consumeClick()) toggle = true;
        while (rangeKey.consumeClick()) range = true;

        if (mc.player == null || mc.level == null) return;

        if (toggle) {
            enabled = !enabled;
            save();
            message(mc, Component.translatable(enabled ? "friendshield.on" : "friendshield.off"));
        }
        if (range) {
            rangeIndex = (rangeIndex + 1) % RANGES.length;
            save();
            message(mc, Component.translatable("friendshield.range", range()));
        }
        if (protect) toggleLookedAtPlayer(mc);

        if (!enabled || ++ticks % 5 != 0) return;
        showCompass(mc);
    }

    private void showCompass(Minecraft mc) {
        List<Player> enemies = new ArrayList<>();
        int friendsNear = 0;
        double r2 = (double) range() * range();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.distanceToSqr(mc.player) > r2) continue;
            if (isProtected(p)) friendsNear++; else enemies.add(p);
        }
        enemies.sort(Comparator.comparingDouble(p -> p.distanceToSqr(mc.player)));

        MutableComponent out;
        if (enemies.isEmpty()) {
            out = Component.translatable("friendshield.none", range()).withStyle(ChatFormatting.GRAY);
        } else {
            Player nearest = enemies.get(0);
            long dist = Math.round(Math.sqrt(nearest.distanceToSqr(mc.player)));
            out = Component.literal(arrowFor(mc.player, nearest) + " ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(nearest.getName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" " + dist + "m").withStyle(ChatFormatting.AQUA));
            if (enemies.size() > 1) {
                out.append(Component.literal("  ").append(
                    Component.translatable("friendshield.more", enemies.size() - 1).withStyle(ChatFormatting.RED)));
            }
        }
        if (friendsNear > 0) {
            out.append(Component.literal("  ").append(
                Component.translatable("friendshield.friends", friendsNear).withStyle(ChatFormatting.GREEN)));
        }
        message(mc, out);
    }

    /** Arrow relative to where the player is currently facing (up = straight ahead). */
    private String arrowFor(Player self, Player target) {
        double dx = target.getX() - self.getX();
        double dz = target.getZ() - self.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = Mth.wrapDegrees(targetYaw - self.getYRot());
        int idx = ((Math.round(rel / 45f) % 8) + 8) % 8;
        return ARROWS[idx];
    }

    private void toggleLookedAtPlayer(Minecraft mc) {
        Entity looked = mc.crosshairPickEntity;
        if (!(looked instanceof Player other) || other == mc.player) {
            message(mc, Component.translatable("friendshield.look").withStyle(ChatFormatting.RED));
            return;
        }
        String name = other.getName().getString();
        String key = name.toLowerCase(Locale.ROOT);
        boolean added = protectedNames.add(key);
        if (!added) protectedNames.remove(key);
        save();
        message(mc, Component.translatable(added ? "friendshield.added" : "friendshield.removed", name)
            .withStyle(added ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    private boolean isProtected(Player p) {
        return protectedNames.contains(p.getName().getString().toLowerCase(Locale.ROOT));
    }

    private int range() { return RANGES[rangeIndex]; }

    /** Single place that shows an action-bar message (26.x: sendOverlayMessage). */
    private static void message(Minecraft mc, Component c) {
        if (mc.player != null) mc.player.sendOverlayMessage(c);
    }

    private void load() {
        if (!Files.isRegularFile(configPath)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
            int idx = Integer.parseInt(p.getProperty("rangeIndex", "2"));
            rangeIndex = Math.max(0, Math.min(RANGES.length - 1, idx));
            for (String s : p.getProperty("protected", "").split(","))
                if (!s.isBlank()) protectedNames.add(s.trim().toLowerCase(Locale.ROOT));
        } catch (IOException | NumberFormatException ignored) { }
    }

    private void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("rangeIndex", Integer.toString(rangeIndex));
        p.setProperty("protected", String.join(",", protectedNames));
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream out = Files.newOutputStream(configPath)) { p.store(out, "Friend Shield settings"); }
        } catch (IOException ignored) { }
    }
}
