package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

@Environment(EnvType.CLIENT)
public class AutoSellFeature {
    private static final int INVENTORY_FULL_THRESHOLD = 32;
    private static final int SELL_COOLDOWN_TICKS = 600;

    private int sellCooldown = 0;
    private boolean active = true;

    private static final List<String> SELL_COMMANDS = List.of(
            "/sell all",
            "/sell hand",
            "/shop sell all",
            "/sellall"
    );

    private String preferredSellCommand = "/sell all";

    private static final Set<Item> FISH_ITEMS = new HashSet<>();
    private static final Set<Item> MOB_DROP_ITEMS = new HashSet<>();

    static {
        FISH_ITEMS.add(Items.COD);
        FISH_ITEMS.add(Items.SALMON);
        FISH_ITEMS.add(Items.PUFFERFISH);
        FISH_ITEMS.add(Items.TROPICAL_FISH);

        MOB_DROP_ITEMS.add(Items.ROTTEN_FLESH);
        MOB_DROP_ITEMS.add(Items.SPIDER_EYE);
        MOB_DROP_ITEMS.add(Items.STRING);
        MOB_DROP_ITEMS.add(Items.BONE);
        MOB_DROP_ITEMS.add(Items.GUNPOWDER);
        MOB_DROP_ITEMS.add(Items.ENDER_PEARL);
        MOB_DROP_ITEMS.add(Items.BLAZE_ROD);
        MOB_DROP_ITEMS.add(Items.NETHER_WART);
        MOB_DROP_ITEMS.add(Items.PHANTOM_MEMBRANE);
        MOB_DROP_ITEMS.add(Items.RABBIT_FOOT);
        MOB_DROP_ITEMS.add(Items.RABBIT_HIDE);
        MOB_DROP_ITEMS.add(Items.FEATHER);
        MOB_DROP_ITEMS.add(Items.LEATHER);
        MOB_DROP_ITEMS.add(Items.IRON_NUGGET);
        MOB_DROP_ITEMS.add(Items.GOLD_NUGGET);
        MOB_DROP_ITEMS.add(Items.SLIME_BALL);
        MOB_DROP_ITEMS.add(Items.MAGMA_CREAM);
        MOB_DROP_ITEMS.add(Items.GHAST_TEAR);
        MOB_DROP_ITEMS.add(Items.WITHER_SKELETON_SKULL);
        MOB_DROP_ITEMS.add(Items.SKELETON_SKULL);
        MOB_DROP_ITEMS.add(Items.ZOMBIE_HEAD);
        MOB_DROP_ITEMS.add(Items.CREEPER_HEAD);
        MOB_DROP_ITEMS.add(Items.PIGLIN_HEAD);
        MOB_DROP_ITEMS.add(Items.DRAGON_HEAD);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public void setSellCommand(String command) {
        this.preferredSellCommand = command;
        AiMod.LOGGER.info("Sell command set to: {}", command);
    }

    public void tick() {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (sellCooldown > 0) {
            sellCooldown--;
            return;
        }

        ClientPlayerEntity player = client.player;
        int filledSlots = countFilledSlots(player);

        if (filledSlots >= INVENTORY_FULL_THRESHOLD) {
            AiMod.LOGGER.info("Inventory is full ({} slots), attempting to sell...", filledSlots);
            attemptSell(client, player);
            sellCooldown = SELL_COOLDOWN_TICKS;
        }
    }

    private void attemptSell(MinecraftClient client, ClientPlayerEntity player) {
        boolean hasSellableItems = hasSellableItems(player);
        if (!hasSellableItems) {
            AiMod.LOGGER.info("No sellable items found in inventory.");
            return;
        }

        sendCommand(client, preferredSellCommand);
        AiMod.LOGGER.info("Sent sell command: {}", preferredSellCommand);

        if (client.player != null) {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("[AI] Inventory full - selling items..."), true
            );
        }
    }

    private void sendCommand(MinecraftClient client, String command) {
        if (client.player == null) return;
        try {
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            client.player.networkHandler.sendCommand(cmd);
        } catch (Exception e) {
            AiMod.LOGGER.error("Failed to send sell command: {}", e.getMessage());
        }
    }

    private boolean hasSellableItems(ClientPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (FISH_ITEMS.contains(item) || MOB_DROP_ITEMS.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private int countFilledSlots(ClientPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            if (!player.getInventory().main.get(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public int countSellableItems(ClientPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (FISH_ITEMS.contains(item) || MOB_DROP_ITEMS.contains(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
