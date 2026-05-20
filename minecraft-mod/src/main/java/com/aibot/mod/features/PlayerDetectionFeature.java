package com.aibot.mod.features;

import com.aibot.mod.AiMod;
import com.aibot.mod.ai.TypingSimulator;
import com.aibot.mod.config.ModConfig;
import com.aibot.mod.macro.MovementPlayback;
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
    private final Set<UUID> creativePlayers = new HashSet<>();
    private final Random random = new Random();

    private MovementPlayback playback = null;

    private PlayerEntity nearestPlayer = null;
    private boolean playerWasNear = false;
    private int greetCooldown = 0;
    private int lookAtPlayerTimer = 0;
    private boolean lookingAtPlayer = false;
    private int crouchTimer = 0;
    private boolean hasCrouched = false;

    private static final String[] CASUAL_GREETINGS = {
        "yo", "hey", "sup", "oh hey", "lol hi", "hi", "hey man"
    };

    public void setPlayback(MovementPlayback playback) {
        this.playback = playback;
    }

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
        boolean playerIsNear = nearestPlayer != null;

        if (playerIsNear && !playerWasNear) {
            onPlayerEnter(client, self, nearestPlayer);
        } else if (!playerIsNear && playerWasNear) {
            onPlayerLeave(client);
        }

        playerWasNear = playerIsNear;

        if (lookingAtPlayer && nearestPlayer != null) {
            lookAtPlayerTimer--;
            mouseMovement.setTarget(nearestPlayer);
            mouseMovement.tick();

            if (!hasCrouched && lookAtPlayerTimer < 40) {
                self.setSneaking(true);
                crouchTimer = 15 + random.nextInt(10);
            }

            if (lookAtPlayerTimer <= 0) {
                lookingAtPlayer = false;
                hasCrouched = false;
                mouseMovement.clearTarget();
            }
        }
    }

    private void onPlayerEnter(MinecraftClient client, ClientPlayerEntity self, PlayerEntity player) {
        AiMod.LOGGER.info("Player entered range: {}", player.getName().getString());

        if (playback != null && playback.isPlaying()) {
            playback.pause();
            AiMod.LOGGER.info("Playback paused — player nearby");
        }

        boolean isCreative = player.getAbilities().creativeMode;
        if (isCreative) {
            creativePlayers.add(player.getUuid());
            AiMod.LOGGER.info("Player {} is creative — will obey commands", player.getName().getString());
        }

        if (!greetedPlayers.contains(player.getUuid()) && greetCooldown <= 0) {
            greetPlayer(player);
            greetedPlayers.add(player.getUuid());
            greetCooldown = 600;
        } else {
            lookingAtPlayer = true;
            lookAtPlayerTimer = 50 + random.nextInt(30);
            hasCrouched = false;
        }
    }

    private void onPlayerLeave(MinecraftClient client) {
        AiMod.LOGGER.info("Player left range — will resume playback");
        lookingAtPlayer = false;
        mouseMovement.clearTarget();

        if (playback != null && playback.isPaused()) {
            int delayMs = (60 + random.nextInt(80)) * 50;
            new Thread(() -> {
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                client.execute(() -> {
                    if (playback.isPaused()) {
                        playback.resume();
                        AiMod.LOGGER.info("Playback resumed after player left");
                    }
                });
            }).start();
        }
    }

    private void greetPlayer(PlayerEntity target) {
        lookingAtPlayer = true;
        lookAtPlayerTimer = 80 + random.nextInt(40);
        hasCrouched = false;

        String greeting = CASUAL_GREETINGS[random.nextInt(CASUAL_GREETINGS.length)];
        TypingSimulator.sendWithDelay(greeting);

        AiMod.LOGGER.info("Greeted player: {}", target.getName().getString());
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

    public boolean isCreativePlayer(UUID uuid) {
        return creativePlayers.contains(uuid);
    }

    public void forgetPlayer(UUID uuid) {
        greetedPlayers.remove(uuid);
        creativePlayers.remove(uuid);
    }

    public PlayerEntity getNearestPlayer() {
        return nearestPlayer;
    }
}
