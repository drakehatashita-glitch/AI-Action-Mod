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
    private final Map<UUID, Integer> suspicionLevel = new HashMap<>();
    private final Map<UUID, Long> lastSeenTimestamp = new HashMap<>();
    private final Random random = new Random();

    private MovementPlayback playback = null;

    private PlayerEntity nearestPlayer = null;
    private boolean playerWasNear = false;
    private int greetCooldown = 0;
    private int lookAtPlayerTimer = 0;
    private boolean lookingAtPlayer = false;
    private int crouchTimer = 0;
    private boolean hasCrouched = false;
    private int multiPlayerCount = 0;

    // How long to remember a player (after they leave, we can re-greet after this)
    private static final long FORGET_PLAYER_MS = 5 * 60 * 1000L;

    private static final String[] CASUAL_GREETINGS = {
        "yo", "hey", "sup", "oh hey", "lol hi", "hi", "hey man", "heyyy", "aye",
        "oh hi", "what's good", "ayo", "what's up"
    };

    private static final String[] RE_GREETINGS = {
        "wb", "back already?", "oh hey again", "welcome back", "lol wb"
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

        List<PlayerEntity> nearbyPlayers = findNearbyOtherPlayers(client, self);
        nearestPlayer = nearbyPlayers.isEmpty() ? null : nearbyPlayers.get(0);
        boolean playerIsNear = nearestPlayer != null;
        int currentCount = nearbyPlayers.size();

        if (playerIsNear && !playerWasNear) {
            onPlayerEnter(client, self, nearestPlayer, nearbyPlayers.size());
        } else if (!playerIsNear && playerWasNear) {
            onPlayerLeave(client);
        } else if (playerIsNear && currentCount > multiPlayerCount) {
            // Additional player entered
            PlayerEntity newest = nearbyPlayers.get(nearbyPlayers.size() - 1);
            if (!greetedPlayers.contains(newest.getUuid())) {
                onPlayerEnter(client, self, newest, nearbyPlayers.size());
            }
        }

        multiPlayerCount = currentCount;
        playerWasNear = playerIsNear;

        // Update creative status for all nearby players
        for (PlayerEntity p : nearbyPlayers) {
            if (p.getAbilities().creativeMode) {
                if (!creativePlayers.contains(p.getUuid())) {
                    creativePlayers.add(p.getUuid());
                    AiMod.LOGGER.info("Player {} is creative — will obey their commands", p.getName().getString());
                }
            }
            lastSeenTimestamp.put(p.getUuid(), System.currentTimeMillis());
        }

        // Forget old players so we can re-greet them later
        tickForgetOldPlayers();

        if (lookingAtPlayer && nearestPlayer != null) {
            lookAtPlayerTimer--;
            mouseMovement.setTarget(nearestPlayer);
            mouseMovement.tick();

            // Crouch acknowledgement for nearby players
            if (!hasCrouched && lookAtPlayerTimer < 35 && random.nextFloat() < 0.7f) {
                self.setSneaking(true);
                crouchTimer = 12 + random.nextInt(14);
            }

            if (lookAtPlayerTimer <= 0) {
                lookingAtPlayer = false;
                hasCrouched = false;
                mouseMovement.clearTarget();
            }
        }
    }

    private void onPlayerEnter(MinecraftClient client, ClientPlayerEntity self, PlayerEntity player, int totalNearby) {
        AiMod.LOGGER.info("Player entered range: {} (total nearby: {})", player.getName().getString(), totalNearby);

        if (playback != null && playback.isPlaying()) {
            playback.pause();
            AiMod.LOGGER.info("Playback paused — player nearby");
        }

        boolean isCreative = player.getAbilities().creativeMode;
        if (isCreative) {
            creativePlayers.add(player.getUuid());
        }

        long now = System.currentTimeMillis();
        boolean hasBeenHere = greetedPlayers.contains(player.getUuid());
        Long lastSeen = lastSeenTimestamp.get(player.getUuid());
        boolean isReturn = hasBeenHere && lastSeen != null && (now - lastSeen) > 30_000;

        if (!hasBeenHere && greetCooldown <= 0) {
            // Only greet based on configured probability
            if (random.nextDouble() < ModConfig.greetingChance) {
                greetPlayer(player, false);
            } else {
                // Just look at them without greeting
                lookingAtPlayer = true;
                lookAtPlayerTimer = 40 + random.nextInt(30);
                hasCrouched = false;
            }
            greetedPlayers.add(player.getUuid());
            greetCooldown = 400 + random.nextInt(200);
        } else if (isReturn && greetCooldown <= 0) {
            // Re-greet returning players sometimes
            if (random.nextFloat() < 0.5f) {
                greetPlayer(player, true);
                greetCooldown = 300;
            }
        } else {
            // Just glance at them
            lookingAtPlayer = true;
            lookAtPlayerTimer = 30 + random.nextInt(40);
            hasCrouched = false;
        }

        // Raise suspicion if many players are nearby (more scrutiny possible)
        if (totalNearby >= 3) {
            suspicionLevel.merge(player.getUuid(), 1, Integer::sum);
        }
    }

    private void onPlayerLeave(MinecraftClient client) {
        AiMod.LOGGER.info("Player(s) left range — will resume playback");
        lookingAtPlayer = false;
        mouseMovement.clearTarget();

        if (playback != null && playback.isPaused()) {
            // Vary resume delay: 3–7 seconds, more if suspicion is elevated
            int baseDelay = 60 + random.nextInt(80);
            int totalDelay = baseDelay * 50; // convert ticks to ms
            new Thread(() -> {
                try { Thread.sleep(totalDelay); } catch (InterruptedException ignored) {}
                client.execute(() -> {
                    if (playback.isPaused()) {
                        playback.resume();
                        AiMod.LOGGER.info("Playback resumed after player left");
                    }
                });
            }).start();
        }
    }

    private void greetPlayer(PlayerEntity target, boolean isReturn) {
        lookingAtPlayer = true;
        lookAtPlayerTimer = 70 + random.nextInt(50);
        hasCrouched = false;

        String greeting;
        if (isReturn) {
            greeting = RE_GREETINGS[random.nextInt(RE_GREETINGS.length)];
        } else {
            greeting = CASUAL_GREETINGS[random.nextInt(CASUAL_GREETINGS.length)];
        }

        TypingSimulator.sendWithDelay(greeting);
        AiMod.LOGGER.info("Greeted player {} (return={})", target.getName().getString(), isReturn);
    }

    private void tickForgetOldPlayers() {
        long now = System.currentTimeMillis();
        Set<UUID> toForget = new HashSet<>();
        for (Map.Entry<UUID, Long> entry : lastSeenTimestamp.entrySet()) {
            if (now - entry.getValue() > FORGET_PLAYER_MS) {
                toForget.add(entry.getKey());
            }
        }
        for (UUID uuid : toForget) {
            greetedPlayers.remove(uuid);
            lastSeenTimestamp.remove(uuid);
            suspicionLevel.remove(uuid);
            AiMod.LOGGER.debug("Forgot player {} (not seen for {}min)", uuid,
                    FORGET_PLAYER_MS / 60000);
        }
    }

    private List<PlayerEntity> findNearbyOtherPlayers(MinecraftClient client, ClientPlayerEntity self) {
        Box searchBox = self.getBoundingBox().expand(ModConfig.playerDetectionRange);
        List<PlayerEntity> players = client.world.getEntitiesByClass(
                PlayerEntity.class,
                searchBox,
                p -> p != self && p.isAlive()
        );
        players.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(self)));
        return players;
    }

    private PlayerEntity findNearestOtherPlayer(MinecraftClient client, ClientPlayerEntity self) {
        List<PlayerEntity> nearby = findNearbyOtherPlayers(client, self);
        return nearby.isEmpty() ? null : nearby.get(0);
    }

    public boolean isCreativePlayer(UUID uuid) {
        return creativePlayers.contains(uuid);
    }

    public void forgetPlayer(UUID uuid) {
        greetedPlayers.remove(uuid);
        creativePlayers.remove(uuid);
        suspicionLevel.remove(uuid);
        lastSeenTimestamp.remove(uuid);
    }

    public PlayerEntity getNearestPlayer() { return nearestPlayer; }
    public int getSuspicionLevel(UUID uuid) { return suspicionLevel.getOrDefault(uuid, 0); }
}
