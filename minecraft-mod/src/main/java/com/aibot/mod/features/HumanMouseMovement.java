package com.aibot.mod.features;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

@Environment(EnvType.CLIENT)
public class HumanMouseMovement {
    private static final Random RANDOM = new Random();

    private float targetYaw;
    private float targetPitch;
    private boolean hasTarget = false;
    private float smoothSpeed = 3.5f;

    public void setTarget(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d playerEyes = client.player.getEyePos();
        Vec3d entityCenter = entity.getBoundingBox().getCenter();
        Vec3d diff = entityCenter.subtract(playerEyes);

        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        targetYaw = (float) (Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        targetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizontalDist)));

        targetYaw += (RANDOM.nextFloat() - 0.5f) * 2.5f;
        targetPitch += (RANDOM.nextFloat() - 0.5f) * 1.5f;

        hasTarget = true;
    }

    public void setTargetAngles(float yaw, float pitch) {
        targetYaw = yaw;
        targetPitch = pitch;
        hasTarget = true;
    }

    public void clearTarget() {
        hasTarget = false;
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    public boolean tick() {
        if (!hasTarget) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;

        ClientPlayerEntity player = client.player;
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        float yawDiff = wrapAngle(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        float speed = smoothSpeed + RANDOM.nextFloat() * 1.5f;
        float moveFraction = 1.0f / speed;

        float newYaw = currentYaw + yawDiff * moveFraction;
        float newPitch = currentPitch + pitchDiff * moveFraction;

        newPitch = Math.max(-90f, Math.min(90f, newPitch));

        player.setYaw(newYaw);
        player.setPitch(newPitch);

        return Math.abs(yawDiff) < 2.0f && Math.abs(pitchDiff) < 2.0f;
    }

    public void jitter() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ClientPlayerEntity player = client.player;
        float jitterYaw = (RANDOM.nextFloat() - 0.5f) * 0.6f;
        float jitterPitch = (RANDOM.nextFloat() - 0.5f) * 0.3f;

        if (RANDOM.nextInt(4) != 0) return;

        player.setYaw(player.getYaw() + jitterYaw);
        player.setPitch(Math.max(-90f, Math.min(90f, player.getPitch() + jitterPitch)));
    }

    private float wrapAngle(float angle) {
        while (angle > 180f) angle -= 360f;
        while (angle < -180f) angle += 360f;
        return angle;
    }
}
