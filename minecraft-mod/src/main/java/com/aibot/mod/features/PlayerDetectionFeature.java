package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.TypingSimulator;
import com.aibot.mod.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.util.*;

@Environment(EnvType.CLIENT)
public class PlayerDetectionFeature {
    private final HumanMouseMovement mouseMovement = new HumanMouseMovement();
    private final Set<UUID> greetedPlayers = new HashSet<>();
    private final Random random = new Random();

    private PlayerEntity nearestPlayer = null;
    private int greetCooldown = 0;
    private int lookAtPlayerTimer = 0;
    private boolean lookingAtPlayer = false;
    private int crouchTimer = 0;
    private boolean hasCrouched = false;

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity self = client.player;

        if (greetCooldown > 0) greetCooldown--;

        if (crouchTimer > 0) {
            crouchTimer--;
            if (crouchTimer == 0) {
                self.setSneaking(false);
                hasCrouched = true;
            }
        }

        nearestPlayer = findNearestOtherPlayer(client, self);

        if (nearestPlayer != null && !greetedPlayers.contains(nearestPlayer.getUuid()) && greetCooldown <= 0) {
            greetPlayer(client, self, nearestPlayer);
            greetedPlayers.add(nearestPlayer.getUuid());
            greetCooldown = 600;
        }

        if (lookingAtPlayer && nearestPlayer != null) {
            lookAtPlayerTimer--;
            mouseMovement.setTarget(nearestPlayer);
            mouseMovement.tick();

            if (!hasCrouched && lookAtPlayerTimer < 30) {
                self.setSneaking(true);
                crouchTimer = 12 + random.nextInt(8);
            }

            if (lookAtPlayerTimer <= 0) {
                lookingAtPlayer = false;
                hasCrouched = false;
                mouseMovement.clearTarget();
            }
        }
    }

    private void greetPlayer(MinecraftClient client, ClientPlayerEntity self, PlayerEntity target) {
        lookingAtPlayer = true;
        lookAtPlayerTimer = 60 + random.nextInt(30);
        hasCrouched = false;

        TypingSimulator.sendWithDelay("yo");

        AiMod.LOGGER.info("Greeting player: {}", target.getName().getString());
    }

    private PlayerEntity findNearestOtherPlayer(MinecraftClient client, ClientPlayerEntity self) {
        Box searchBox = self.getBoundingBox().expand(ModConfig.playerDetectionRange);
        List<PlayerEntity> players = client.world.getEntitiesByClass(
                PlayerEntity.class,
                searchBox,
                p -> p != self && p.isAlive()
        );

        return players.stream()
                .min(Comparator.comparingDouble(p -> p.squaredDistanceTo(self)))
                .orElse(null);
    }

    public void forgetPlayer(UUID uuid) {
        greetedPlayers.remove(uuid);
    }

    public PlayerEntity getNearestPlayer() {
        return nearestPlayer;
    }
}
