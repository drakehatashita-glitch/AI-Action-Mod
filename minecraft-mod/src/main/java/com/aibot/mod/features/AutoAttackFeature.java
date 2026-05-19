package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
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

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            currentTarget = null;
            mouseMovement.clearTarget();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.setSneaking(false);
            }
        } else {
            AiMod.LOGGER.info("Auto-attack enabled");
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

        if (crouchTimer > 0) {
            crouchTimer--;
            if (crouchTimer == 0) {
                player.setSneaking(false);
                isCrouching = false;
            }
            return;
        }

        if (repositionCooldown > 0) {
            repositionCooldown--;
        }

        currentTarget = findNearestHostile(client, player);

        if (currentTarget == null || !currentTarget.isAlive()) {
            currentTarget = null;
            mouseMovement.clearTarget();
            lastPlayerPos = null;
            return;
        }

        mouseMovement.setTarget(currentTarget);
        boolean lookingAt = mouseMovement.tick();

        double distToTarget = player.distanceTo(currentTarget);

        if (distToTarget > ModConfig.attackRange + 1.5) {
            moveTowardTarget(client, player);
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (lookingAt && attackCooldown <= 0 && distToTarget <= ModConfig.attackRange) {
            client.interactionManager.attackEntity(player, currentTarget);
            attackCooldown = 10 + random.nextInt(4);
        }

        lastPlayerPos = player.getPos();
    }

    public void onDisplacedByPlayer() {
        if (!active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || currentTarget == null) return;

        ClientPlayerEntity player = client.player;

        player.setSneaking(true);
        isCrouching = true;
        crouchTimer = 15 + random.nextInt(10);

        if (currentTarget != null) {
            mouseMovement.setTarget(currentTarget);
        }

        repositionCooldown = 20;
    }

    private void moveTowardTarget(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = currentTarget.getPos();
        Vec3d dir = targetPos.subtract(playerPos).normalize();

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        mouseMovement.setTargetAngles(targetYaw, player.getPitch());

        client.options.forwardKey.setPressed(true);
    }

    private LivingEntity findNearestHostile(MinecraftClient client, ClientPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(ModConfig.attackRange + 4.0);
        List<LivingEntity> hostiles = client.world.getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                e -> e instanceof HostileEntity && e.isAlive() && e != player
        );

        return hostiles.stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
                .orElse(null);
    }
}
