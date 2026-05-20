package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.*;

@Environment(EnvType.CLIENT)
public class AutoSellFeature {
    private static final int SELL_COOLDOWN_TICKS = 600;
    private static final int GUI_WAIT_TICKS = 20;

    private int sellCooldown = 0;
    private boolean active = true;
    private boolean waitingForGui = false;
    private int guiWaitTimer = 0;
    private int sellAttemptCount = 0;

    // Multiple sell command fallbacks to try in sequence
    private static final String[] SELL_COMMANDS = {
        "/sell all", "/sellall", "/sell hand", "/shop sellall",
        "/market sellall", "/ah sell all", "/trade sell"
    };
    private int sellCommandIndex = 0;

    private static final List<String> SELL_BUTTON_NAMES = List.of(
            "sell all", "sell items", "sell all items", "confirm sale",
            "confirm", "yes", "accept", "ok", "sell", "click to sell",
            "sell everything", "confirm sell", "sell all fish",
            "sell all drops", "complete sale", "proceed", "sell now",
            "quick sell", "auto sell", "bulk sell", "instant sell"
    );

    private static final Set<Item> FISH_ITEMS = new HashSet<>();
    private static final Set<Item> MOB_DROP_ITEMS = new HashSet<>();
    private static final Set<Item> CROP_ITEMS = new HashSet<>();
    private static final Set<Item> ORE_ITEMS = new HashSet<>();

    // Session stats
    private int totalSellCount = 0;

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
        MOB_DROP_ITEMS.add(Items.BONE_MEAL);
        MOB_DROP_ITEMS.add(Items.ARROW);
        MOB_DROP_ITEMS.add(Items.TIPPED_ARROW);
        MOB_DROP_ITEMS.add(Items.SPECTRAL_ARROW);

        CROP_ITEMS.add(Items.WHEAT);
        CROP_ITEMS.add(Items.CARROT);
        CROP_ITEMS.add(Items.POTATO);
        CROP_ITEMS.add(Items.BEETROOT);
        CROP_ITEMS.add(Items.MELON_SLICE);
        CROP_ITEMS.add(Items.PUMPKIN);
        CROP_ITEMS.add(Items.SUGAR_CANE);
        CROP_ITEMS.add(Items.COCOA_BEANS);
        CROP_ITEMS.add(Items.NETHER_WART);

        ORE_ITEMS.add(Items.IRON_INGOT);
        ORE_ITEMS.add(Items.GOLD_INGOT);
        ORE_ITEMS.add(Items.DIAMOND);
        ORE_ITEMS.add(Items.EMERALD);
        ORE_ITEMS.add(Items.COAL);
        ORE_ITEMS.add(Items.LAPIS_LAZULI);
        ORE_ITEMS.add(Items.REDSTONE);
        ORE_ITEMS.add(Items.QUARTZ);
        ORE_ITEMS.add(Items.RAW_IRON);
        ORE_ITEMS.add(Items.RAW_GOLD);
        ORE_ITEMS.add(Items.RAW_COPPER);
        ORE_ITEMS.add(Items.AMETHYST_SHARD);
    }

    public void setActive(boolean active) { this.active = active; }
    public boolean isActive() { return active; }

    public void setSellCommand(String command) {
        SELL_COMMANDS[0] = command;
        sellCommandIndex = 0;
        AiMod.LOGGER.info("Primary sell command set to: {}", command);
    }

    public void tick() {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (waitingForGui) {
            handleGuiWait(client);
            return;
        }

        if (sellCooldown > 0) {
            sellCooldown--;
            return;
        }

        ClientPlayerEntity player = client.player;
        int filledSlots = countFilledSlots(player);
        int threshold = ModConfig.sellInventoryThreshold;

        if (filledSlots >= threshold && hasSellableItems(player)) {
            int sellableCount = countSellableItems(player);
            AiMod.LOGGER.info("Inventory threshold reached ({}/{} slots, {} sellable items) — selling",
                    filledSlots, 36, sellableCount);
            attemptSell(client, player, sellableCount);
        }
    }

    private void handleGuiWait(MinecraftClient client) {
        guiWaitTimer--;

        Screen currentScreen = client.currentScreen;
        if (currentScreen instanceof HandledScreen<?> handledScreen) {
            AiMod.LOGGER.info("GUI open after sell command: {}", currentScreen.getTitle().getString());
            boolean clicked = tryClickSellButton(client, handledScreen);
            if (clicked) {
                totalSellCount++;
                sellAttemptCount = 0; // Reset fallback counter on success
            } else {
                AiMod.LOGGER.info("No sell button found — closing screen");
                client.execute(() -> client.setScreen(null));
            }
            waitingForGui = false;
            sellCooldown = SELL_COOLDOWN_TICKS;
            return;
        }

        if (guiWaitTimer <= 0) {
            waitingForGui = false;
            // Try next fallback command if this one didn't open a GUI
            if (sellAttemptCount < SELL_COMMANDS.length - 1) {
                sellAttemptCount++;
                sellCommandIndex = sellAttemptCount % SELL_COMMANDS.length;
                AiMod.LOGGER.info("Sell command didn't open GUI, trying: {}", SELL_COMMANDS[sellCommandIndex]);
                sendCommand(client, SELL_COMMANDS[sellCommandIndex]);
                waitingForGui = true;
                guiWaitTimer = GUI_WAIT_TICKS;
            } else {
                sellCooldown = SELL_COOLDOWN_TICKS;
                sellAttemptCount = 0;
            }
        }
    }

    private boolean tryClickSellButton(MinecraftClient client, HandledScreen<?> screen) {
        var handler = screen.getScreenHandler();
        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String itemName = stack.getName().getString().toLowerCase();
            for (String keyword : SELL_BUTTON_NAMES) {
                if (itemName.contains(keyword)) {
                    candidates.add(i);
                    break;
                }
            }

            if (hasLoreKeyword(stack)) {
                if (!candidates.contains(i)) candidates.add(i);
            }
        }

        if (candidates.isEmpty()) return false;

        int bestSlot = pickBestSlot(handler, candidates);
        AiMod.LOGGER.info("Clicking sell button at slot {} ('{}')",
                bestSlot, handler.slots.get(bestSlot).getStack().getName().getString());

        client.interactionManager.clickSlot(
                handler.syncId, bestSlot, 0, SlotActionType.PICKUP, client.player
        );
        return true;
    }

    private boolean hasLoreKeyword(ItemStack stack) {
        var lore = stack.get(net.minecraft.component.DataComponentTypes.LORE);
        if (lore == null) return false;
        for (Text line : lore.lines()) {
            String text = line.getString().toLowerCase();
            for (String keyword : SELL_BUTTON_NAMES) {
                if (text.contains(keyword)) return true;
            }
        }
        return false;
    }

    private int pickBestSlot(net.minecraft.screen.ScreenHandler handler, List<Integer> candidates) {
        String[] priority = {"sell all", "sell everything", "sell items", "quick sell",
                "auto sell", "confirm", "yes", "ok", "sell"};
        for (String keyword : priority) {
            for (int slotIdx : candidates) {
                String name = handler.slots.get(slotIdx).getStack().getName().getString().toLowerCase();
                if (name.contains(keyword)) return slotIdx;
            }
        }
        return candidates.get(0);
    }

    private void attemptSell(MinecraftClient client, ClientPlayerEntity player, int sellableCount) {
        String command = SELL_COMMANDS[sellCommandIndex % SELL_COMMANDS.length];
        sendCommand(client, command);
        waitingForGui = true;
        guiWaitTimer = GUI_WAIT_TICKS;

        player.sendMessage(Text.literal("[AI] Selling " + sellableCount + " items (" + command + ")..."), true);
        AiMod.LOGGER.info("Sent sell command: {}", command);
    }

    private void sendCommand(MinecraftClient client, String command) {
        if (client.player == null) return;
        try {
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            client.player.networkHandler.sendChatCommand(cmd);
        } catch (Exception e) {
            AiMod.LOGGER.error("Failed to send sell command: {}", e.getMessage());
        }
    }

    private boolean hasSellableItems(ClientPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && isSellable(stack.getItem())) return true;
        }
        return false;
    }

    private boolean isSellable(Item item) {
        return FISH_ITEMS.contains(item) || MOB_DROP_ITEMS.contains(item)
                || CROP_ITEMS.contains(item) || ORE_ITEMS.contains(item);
    }

    private int countFilledSlots(ClientPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (!player.getInventory().getStack(i).isEmpty()) count++;
        }
        return count;
    }

    public int countSellableItems(ClientPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && isSellable(stack.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int getTotalSellCount() { return totalSellCount; }
}
