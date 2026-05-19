package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

@Environment(EnvType.CLIENT)
public class AutoFishFeature {
    private boolean active = false;
    private final HumanMouseMovement mouseMovement = new HumanMouseMovement();
    private final Random random = new Random();

    private int castCooldown = 0;
    private int hookCheckTimer = 0;
    private boolean waitingForBite = false;
    private int noWaterWarnTimer = 0;

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

        if (!isNearWater(client, player)) {
            noWaterWarnTimer++;
            if (noWaterWarnTimer % 100 == 0) {
                player.sendMessage(
                    net.minecraft.text.Text.literal("[AI] No water nearby - stand next to water to fish!"), true
                );
            }
            return;
        }
        noWaterWarnTimer = 0;

        mouseMovement.jitter();

        if (castCooldown > 0) {
            castCooldown--;
            return;
        }

        FishingBobberEntity bobber = player.fishHook;

        if (bobber == null) {
            if (!waitingForBite) {
                lookTowardWater(client, player);
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

    private boolean isNearWater(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int searchRadius = 5;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    var block = client.world.getBlockState(pos).getBlock();
                    if (block == Blocks.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void lookTowardWater(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int searchRadius = 5;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                BlockPos pos = playerPos.add(x, -1, z);
                var block = client.world.getBlockState(pos).getBlock();
                if (block == Blocks.WATER) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-x, z));
                    player.setYaw(yaw);
                    player.setPitch(25f + random.nextFloat() * 15f);
                    return;
                }
            }
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
