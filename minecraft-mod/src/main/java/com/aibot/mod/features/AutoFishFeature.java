package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.Random;

@Environment(EnvType.CLIENT)
public class AutoFishFeature {
    private boolean active = false;
    private final HumanMouseMovement mouseMovement = new HumanMouseMovement();
    private final Random random = new Random();

    private int castCooldown = 0;
    private int hookCheckTimer = 0;
    private boolean waitingForBite = false;

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            waitingForBite = false;
            castCooldown = 0;
        } else {
            AiMod.LOGGER.info("Auto-fish enabled");
        }
    }

    public boolean isActive() {
        return active;
    }

    public void tick() {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;

        if (!isHoldingFishingRod(player)) return;

        mouseMovement.jitter();

        if (castCooldown > 0) {
            castCooldown--;
            return;
        }

        FishingBobberEntity bobber = player.fishHook;

        if (bobber == null) {
            if (!waitingForBite) {
                cast(client, player);
                waitingForBite = true;
                hookCheckTimer = 0;
            }
            return;
        }

        hookCheckTimer++;

        if (hookCheckTimer > 20) {
            if (hasFishOnHook(bobber)) {
                reelIn(client, player);
                waitingForBite = false;
                castCooldown = 10 + random.nextInt(15);
            }
        }

        if (hookCheckTimer > 400 + random.nextInt(100)) {
            reelIn(client, player);
            waitingForBite = false;
            castCooldown = 5 + random.nextInt(10);
        }
    }

    private void cast(MinecraftClient client, ClientPlayerEntity player) {
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
    }

    private void reelIn(MinecraftClient client, ClientPlayerEntity player) {
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
    }

    private boolean hasFishOnHook(FishingBobberEntity bobber) {
        return bobber.getHookedEntity() != null || bobber.isSubmergedInWater();
    }

    private boolean isHoldingFishingRod(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        return mainHand.getItem() instanceof FishingRodItem
                || offHand.getItem() instanceof FishingRodItem;
    }
}
