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
import net.minecraft.util.math.Vec3d;

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

    // Anti-AFK behaviour while waiting
    private int idleBehaviourTimer = 0;
    private int idlePhase = 0;

    // Bobber position tracking for motion-based bite detection
    private double lastBobberY = Double.NaN;
    private double prevBobberY = Double.NaN;
    private int motionSampleTimer = 0;

    // Rod durability warning
    private int lastKnownDurability = -1;

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            waitingForBite = false;
            castCooldown = 0;
            idleBehaviourTimer = 0;
            lastBobberY = Double.NaN;
        } else {
            AiMod.LOGGER.info("Auto-fish enabled");
        }
    }

    public boolean isActive() { return active; }

    public void tick() {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;

        if (!isHoldingFishingRod(player)) {
            player.sendMessage(
                net.minecraft.text.Text.literal("[AI] No fishing rod in hand — equip one to fish!"), true);
            return;
        }

        checkRodDurability(player);

        if (!isNearWater(client, player)) {
            noWaterWarnTimer++;
            if (noWaterWarnTimer % 120 == 0) {
                player.sendMessage(
                    net.minecraft.text.Text.literal("[AI] No water nearby — move next to water!"), true);
            }
            return;
        }
        noWaterWarnTimer = 0;

        // Subtle idle movements while waiting for a bite (looks more human)
        tickIdleBehaviour(client, player);

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
                lastBobberY = Double.NaN;
                prevBobberY = Double.NaN;
                motionSampleTimer = 0;
            }
            return;
        }

        hookCheckTimer++;

        // Track bobber Y position for motion-based bite detection
        if (hookCheckTimer > 15) {
            trackBobberMotion(bobber);

            if (hasBiteByMotion()) {
                AiMod.LOGGER.info("Bite detected via bobber motion!");
                reelIn(client, player);
                waitingForBite = false;
                // Randomise cast cooldown to vary the rhythm
                castCooldown = 8 + random.nextInt(20);
                lastBobberY = Double.NaN;
                return;
            }

            // Fallback: entity hook check
            if (hasFishOnHook(bobber)) {
                reelIn(client, player);
                waitingForBite = false;
                castCooldown = 8 + random.nextInt(18);
                lastBobberY = Double.NaN;
                return;
            }
        }

        // Timeout: reel in if bobber has been out too long (vary this to avoid pattern)
        int timeout = 380 + random.nextInt(120);
        if (hookCheckTimer > timeout) {
            reelIn(client, player);
            waitingForBite = false;
            castCooldown = 4 + random.nextInt(12);
            lastBobberY = Double.NaN;
        }
    }

    private void trackBobberMotion(FishingBobberEntity bobber) {
        motionSampleTimer++;
        if (motionSampleTimer % 2 != 0) return;

        prevBobberY = lastBobberY;
        lastBobberY = bobber.getY();
    }

    private boolean hasBiteByMotion() {
        if (Double.isNaN(prevBobberY) || Double.isNaN(lastBobberY)) return false;
        double drop = prevBobberY - lastBobberY;
        // A fish bite causes the bobber to dip down sharply
        return drop > 0.06;
    }

    private void tickIdleBehaviour(MinecraftClient client, ClientPlayerEntity player) {
        idleBehaviourTimer--;
        if (idleBehaviourTimer > 0) return;

        // Rotate through different idle behaviours
        idlePhase = (idlePhase + 1) % 4;
        switch (idlePhase) {
            case 0 -> {
                // Gentle yaw adjustment, looking slightly around
                float offsetYaw = (random.nextFloat() - 0.5f) * 8f;
                mouseMovement.setTargetAngles(player.getYaw() + offsetYaw,
                        22f + random.nextFloat() * 12f);
                idleBehaviourTimer = 40 + random.nextInt(60);
            }
            case 1 -> {
                // Micro head-jitter (already handled by mouseMovement.jitter)
                mouseMovement.jitter();
                idleBehaviourTimer = 20 + random.nextInt(30);
            }
            case 2 -> {
                // Occasionally glance down at inventory (look down briefly)
                if (random.nextFloat() < 0.3f) {
                    client.execute(() -> player.setPitch(55f + random.nextFloat() * 15f));
                }
                idleBehaviourTimer = 30 + random.nextInt(50);
            }
            case 3 -> {
                // Reset to looking at water
                lookTowardWater(client, player);
                idleBehaviourTimer = 60 + random.nextInt(80);
            }
        }
    }

    private boolean isNearWater(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int searchRadius = 6;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    var block = client.world.getBlockState(pos).getBlock();
                    if (block == Blocks.WATER) return true;
                }
            }
        }
        return false;
    }

    private void lookTowardWater(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int searchRadius = 6;

        BlockPos bestWater = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                for (int y = -1; y >= -3; y--) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (client.world.getBlockState(pos).getBlock() == Blocks.WATER) {
                        double dist = x * x + z * z;
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestWater = pos;
                        }
                        break;
                    }
                }
            }
        }

        if (bestWater != null) {
            int dx = bestWater.getX() - playerPos.getX();
            int dz = bestWater.getZ() - playerPos.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            // Add small random offset so it doesn't look perfectly aimed
            yaw += (random.nextFloat() - 0.5f) * 6f;
            player.setYaw(yaw);
            player.setPitch(22f + random.nextFloat() * 14f);
        }
    }

    private void cast(MinecraftClient client, ClientPlayerEntity player) {
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        AiMod.LOGGER.debug("Cast fishing rod");
    }

    private void reelIn(MinecraftClient client, ClientPlayerEntity player) {
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        AiMod.LOGGER.debug("Reeled in fishing rod");
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

    private void checkRodDurability(ClientPlayerEntity player) {
        ItemStack rod = player.getMainHandStack();
        if (!(rod.getItem() instanceof FishingRodItem)) {
            rod = player.getOffHandStack();
        }
        if (!(rod.getItem() instanceof FishingRodItem)) return;

        int maxDurability = rod.getMaxDamage();
        int currentDamage = rod.getDamage();
        int remaining = maxDurability - currentDamage;

        if (lastKnownDurability != remaining) {
            lastKnownDurability = remaining;
            if (remaining > 0 && remaining <= 20) {
                player.sendMessage(
                    net.minecraft.text.Text.literal("[AI] Warning: Fishing rod almost broken! (" + remaining + " uses left)"), true);
            }
        }
    }
}
