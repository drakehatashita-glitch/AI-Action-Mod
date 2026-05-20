package com.aibot.mod.mixin;

import com.aibot.mod.features.FeatureManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts outgoing chat messages and commands so the recorder can
 * capture them as part of a macro sequence.
 *
 * sendMessage(String, boolean) is called by ChatScreen when the player
 * presses Enter — the first argument is the full text (commands keep
 * their leading '/').
 */
@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"))
    private void aimod$onSendMessage(String chatText, boolean addToHistory,
                                     CallbackInfo ci) {
        FeatureManager instance = FeatureManager.getInstance();
        if (instance != null) {
            instance.getRecorder().recordChatOrCommand(chatText);
        }
    }
}
