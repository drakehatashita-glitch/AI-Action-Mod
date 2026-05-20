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

    // Current velocity (for inertia simulation)
    private float yawVelocity = 0f;
    private float pitchVelocity = 0f;

    // Per-target noise offset (refreshed when target changes)
    private float aimNoiseYaw = 0f;
    private float aimNoisePitch = 0f;
    private int noiseRefreshTimer = 0;

    // Bezier control point for curved movement
    private float bezierCpYaw = 0f;
    private float bezierCpPitch = 0f;
    private float bezierProgress = 0f;
    private float bezierStartYaw = 0f;
    private float bezierStartPitch = 0f;
    private boolean bezierActive = false;

    // Idle micro-drift
    private float driftYaw = 0f;
    private float driftPitch = 0f;
    private int driftTimer = 0;

    public void setTarget(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d playerEyes = client.player.getEyePos();
        // Aim at a slightly random point on the entity bounding box (not always center)
        Vec3d box = entity.getBoundingBox().getCenter();
        double heightOffset = (RANDOM.nextFloat() - 0.3f) * entity.getBoundingBox().getLengthY() * 0.4;
        Vec3d aimPoint = new Vec3d(box.x, box.y + heightOffset, box.z);

        Vec3d diff = aimPoint.subtract(playerEyes);
        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float newTargetYaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float newTargetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizontalDist)));

        // Only start a new bezier curve if target changed significantly
        if (!hasTarget || Math.abs(wrapAngle(newTargetYaw - targetYaw)) > 5f) {
            if (client.player != null) {
                bezierStartYaw = client.player.getYaw();
                bezierStartPitch = client.player.getPitch();
            }
            bezierCpYaw = newTargetYaw + (RANDOM.nextFloat() - 0.5f) * 20f;
            bezierCpPitch = newTargetPitch + (RANDOM.nextFloat() - 0.5f) * 10f;
            bezierProgress = 0f;
            bezierActive = true;
        }

        targetYaw = newTargetYaw;
        targetPitch = newTargetPitch;

        // Per-target aim noise (simulates human imperfect aim)
        aimNoiseYaw = (RANDOM.nextFloat() - 0.5f) * 2.2f;
        aimNoisePitch = (RANDOM.nextFloat() - 0.5f) * 1.2f;
        noiseRefreshTimer = 8 + RANDOM.nextInt(12);

        hasTarget = true;
    }

    public void setTargetAngles(float yaw, float pitch) {
        targetYaw = yaw;
        targetPitch = pitch;
        if (!hasTarget) {
            bezierActive = false;
        }
        hasTarget = true;
    }

    public void clearTarget() {
        hasTarget = false;
        bezierActive = false;
        yawVelocity = 0f;
        pitchVelocity = 0f;
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    public boolean tick() {
        if (!hasTarget) {
            tickIdleDrift();
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;

        ClientPlayerEntity player = client.player;
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        // Refresh aim noise periodically
        noiseRefreshTimer--;
        if (noiseRefreshTimer <= 0) {
            aimNoiseYaw = (RANDOM.nextFloat() - 0.5f) * 1.8f;
            aimNoisePitch = (RANDOM.nextFloat() - 0.5f) * 0.9f;
            noiseRefreshTimer = 6 + RANDOM.nextInt(10);
        }

        float finalTargetYaw = targetYaw + aimNoiseYaw;
        float finalTargetPitch = targetPitch + aimNoisePitch;

        float newYaw, newPitch;

        if (bezierActive && bezierProgress < 1f) {
            // Bezier curve approach for initial movement (curved, natural-looking)
            bezierProgress += 0.06f + RANDOM.nextFloat() * 0.04f;
            if (bezierProgress >= 1f) {
                bezierProgress = 1f;
                bezierActive = false;
            }
            float t = bezierProgress;
            float oneMinusT = 1f - t;
            // Quadratic bezier: B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
            newYaw = oneMinusT * oneMinusT * bezierStartYaw
                    + 2f * oneMinusT * t * bezierCpYaw
                    + t * t * finalTargetYaw;
            newPitch = oneMinusT * oneMinusT * bezierStartPitch
                    + 2f * oneMinusT * t * bezierCpPitch
                    + t * t * finalTargetPitch;
        } else {
            // Spring-damper for fine correction (inertia-based)
            float yawDiff = wrapAngle(finalTargetYaw - currentYaw);
            float pitchDiff = finalTargetPitch - currentPitch;

            float springStrength = 0.18f + RANDOM.nextFloat() * 0.06f;
            float damping = 0.72f;

            yawVelocity = yawVelocity * damping + yawDiff * springStrength;
            pitchVelocity = pitchVelocity * damping + pitchDiff * springStrength;

            // Clamp velocity to prevent overshooting wildly
            yawVelocity = Math.max(-8f, Math.min(8f, yawVelocity));
            pitchVelocity = Math.max(-5f, Math.min(5f, pitchVelocity));

            newYaw = currentYaw + yawVelocity;
            newPitch = currentPitch + pitchVelocity;
        }

        newPitch = Math.max(-90f, Math.min(90f, newPitch));
        player.setYaw(newYaw);
        player.setPitch(newPitch);

        float yawErr = Math.abs(wrapAngle(finalTargetYaw - newYaw));
        float pitchErr = Math.abs(finalTargetPitch - newPitch);
        return yawErr < 2.5f && pitchErr < 2.5f;
    }

    private void tickIdleDrift() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        driftTimer--;
        if (driftTimer <= 0) {
            driftYaw = (RANDOM.nextFloat() - 0.5f) * 0.5f;
            driftPitch = (RANDOM.nextFloat() - 0.5f) * 0.25f;
            driftTimer = 12 + RANDOM.nextInt(20);
        }

        // Only apply drift occasionally, not every tick
        if (RANDOM.nextInt(5) != 0) return;
        ClientPlayerEntity player = client.player;
        player.setYaw(player.getYaw() + driftYaw);
        player.setPitch(Math.max(-90f, Math.min(90f, player.getPitch() + driftPitch)));
    }

    public void jitter() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Only jitter 1 in 5 ticks, and use a small Gaussian for realism
        if (RANDOM.nextInt(5) != 0) return;

        ClientPlayerEntity player = client.player;
        float jitterYaw = (float) (RANDOM.nextGaussian() * 0.25);
        float jitterPitch = (float) (RANDOM.nextGaussian() * 0.12);

        player.setYaw(player.getYaw() + jitterYaw);
        player.setPitch(Math.max(-90f, Math.min(90f, player.getPitch() + jitterPitch)));
    }

    private float wrapAngle(float angle) {
        while (angle > 180f) angle -= 360f;
        while (angle < -180f) angle += 360f;
        return angle;
    }
}
