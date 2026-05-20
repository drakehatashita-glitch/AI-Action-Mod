package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class AutoAttackFeature {
    private boolean active = false;
    private final HumanMouseMovement mouseMovement = new HumanMouseMovement();
    private final Random random = new Random();

    private LivingEntity currentTarget = null;
    private int attackCooldown = 0;
    private int crouchTimer = 0;
    private boolean isCrouching = false;
    private Vec3d lastPlayerPos = null;
    private int repositionCooldown = 0;

    // Strafe behaviour
    private int strafeTimer = 0;
    private int strafeDirection = 0; // -1 left, 0 none, 1 right
    private int strafeChangeTimer = 0;

    // W-tap rhythm for crits
    private boolean wTapPhase = false;
    private int wTapTimer = 0;

    // Retreat logic
    private boolean retreating = false;
    private int retreatTimer = 0;

    // Sprint state tracking
    private boolean isSprinting = false;

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            currentTarget = null;
            mouseMovement.clearTarget();
            strafeDirection = 0;
            retreating = false;
            wTapPhase = false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.setSneaking(false);
                client.options.forwardKey.setPressed(false);
                client.options.leftKey.setPressed(false);
                client.options.rightKey.setPressed(false);
                client.options.sprintKey.setPressed(false);
            }
        } else {
            AiMod.LOGGER.info("Auto-attack enabled");
        }
    }

    public boolean isActive() { return active; }

    public void tick() {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;

        if (crouchTimer > 0) {
            crouchTimer--;
            if (crouchTimer == 0) {
                player.setSneaking(false);
                isCrouching = false;
            }
        }

        if (repositionCooldown > 0) repositionCooldown--;

        // Low-health retreat
        float healthPct = player.getHealth() / player.getMaxHealth();
        if (healthPct < 0.25f && !retreating) {
            startRetreat(client, player);
        }

        if (retreating) {
            tickRetreat(client, player);
            return;
        }

        // Find best target (prefer low-health enemies nearby)
        currentTarget = findBestTarget(client, player);

        if (currentTarget == null || !currentTarget.isAlive()) {
            currentTarget = null;
            mouseMovement.clearTarget();
            lastPlayerPos = null;
            strafeDirection = 0;
            releaseMovementKeys(client);
            return;
        }

        mouseMovement.setTarget(currentTarget);
        boolean lookingAt = mouseMovement.tick();

        double distToTarget = player.distanceTo(currentTarget);

        // Update strafe behaviour
        tickStrafe(client, player, distToTarget);

        // Move toward target if too far
        if (distToTarget > ModConfig.attackRange + 1.0 && !isCrouching) {
            moveTowardTarget(client, player);
        } else if (distToTarget <= ModConfig.attackRange) {
            client.options.forwardKey.setPressed(false);
        }

        // W-tap rhythm (release forward momentarily before strike for crit)
        tickWTap(client, distToTarget);

        if (attackCooldown > 0) attackCooldown--;

        if (lookingAt && attackCooldown <= 0 && distToTarget <= ModConfig.attackRange) {
            client.interactionManager.attackEntity(player, currentTarget);
            // Vary the cooldown to simulate human timing (aim for after attack cooldown, ~10 ticks)
            attackCooldown = 9 + random.nextInt(5);
        }

        lastPlayerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
    }

    private void tickStrafe(MinecraftClient client, ClientPlayerEntity player, double dist) {
        strafeChangeTimer--;
        if (strafeChangeTimer <= 0) {
            // Randomly change strafe direction or stop
            int roll = random.nextInt(5);
            strafeDirection = roll == 0 ? -1 : (roll == 1 ? 1 : 0);
            strafeChangeTimer = 15 + random.nextInt(25);
        }

        if (dist > ModConfig.attackRange + 2.0) {
            // Too far, don't strafe — focus on closing distance
            strafeDirection = 0;
        }

        client.options.leftKey.setPressed(strafeDirection == -1);
        client.options.rightKey.setPressed(strafeDirection == 1);
    }

    private void tickWTap(MinecraftClient client, double dist) {
        if (dist > ModConfig.attackRange) return;

        wTapTimer--;
        if (wTapTimer <= 0) {
            wTapPhase = !wTapPhase;
            wTapTimer = wTapPhase ? 2 : 5 + random.nextInt(3);
        }

        if (wTapPhase) {
            client.options.forwardKey.setPressed(false);
        }
    }

    private void startRetreat(MinecraftClient client, ClientPlayerEntity player) {
        retreating = true;
        retreatTimer = 30 + random.nextInt(20);
        AiMod.LOGGER.info("Low health — retreating!");
        if (client.player != null) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("[AI] Low health! Retreating..."), true);
        }
    }

    private void tickRetreat(MinecraftClient client, ClientPlayerEntity player) {
        retreatTimer--;

        if (retreatTimer <= 0 || player.getHealth() / player.getMaxHealth() > 0.5f) {
            retreating = false;
            releaseMovementKeys(client);
            return;
        }

        // Run away from target
        if (currentTarget != null) {
            Vec3d toTarget = new Vec3d(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ())
                    .subtract(player.getX(), player.getY(), player.getZ()).normalize();
            float fleeYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z)) + 180f;
            mouseMovement.setTargetAngles(fleeYaw, player.getPitch());
            mouseMovement.tick();
        }

        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(true);
    }

    public void onDisplacedByPlayer() {
        if (!active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || currentTarget == null) return;

        ClientPlayerEntity player = client.player;
        player.setSneaking(true);
        isCrouching = true;
        crouchTimer = 12 + random.nextInt(12);

        if (currentTarget != null) mouseMovement.setTarget(currentTarget);
        repositionCooldown = 20;
    }

    private void moveTowardTarget(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d dir = new Vec3d(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ())
                .subtract(player.getX(), player.getY(), player.getZ()).normalize();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        mouseMovement.setTargetAngles(targetYaw, player.getPitch());

        client.options.forwardKey.setPressed(true);
        // Sprint when far enough away
        boolean shouldSprint = player.distanceTo(currentTarget) > ModConfig.attackRange + 2.0;
        client.options.sprintKey.setPressed(shouldSprint);
    }

    private LivingEntity findBestTarget(MinecraftClient client, ClientPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(ModConfig.attackRange + 5.0);
        List<LivingEntity> hostiles = client.world.getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                e -> e instanceof HostileEntity && e.isAlive() && e != player
        );

        if (hostiles.isEmpty()) return null;

        // Score: closer = better, lower health = higher priority
        return hostiles.stream().min(Comparator.comparingDouble(e -> {
            double dist = e.squaredDistanceTo(player);
            double healthPenalty = (e.getHealth() / e.getMaxHealth()) * 8.0; // prefer lower health
            return dist + healthPenalty;
        })).orElse(null);
    }

    private void releaseMovementKeys(MinecraftClient client) {
        client.options.forwardKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
    }
}
