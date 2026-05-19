package com.aibot.mod;

import com.aibot.mod.config.ModConfig;
import com.aibot.mod.features.FeatureManager;
import com.aibot.mod.keybind.ModKeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class AiModClient implements ClientModInitializer {

    public static FeatureManager featureManager;

    @Override
    public void onInitializeClient() {
        AiMod.LOGGER.info("AI Mod client initializing...");
        ModConfig.load();
        ModKeyBindings.register();
        featureManager = new FeatureManager();
        featureManager.init();
        AiMod.LOGGER.info("AI Mod ready. Press [K] to toggle fishing, [L] to toggle attack, [;] to toggle all.");
    }
}
