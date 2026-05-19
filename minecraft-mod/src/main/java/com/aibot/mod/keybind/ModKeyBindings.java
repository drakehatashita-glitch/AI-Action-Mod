package com.aibot.mod.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static KeyBinding toggleFish;
    public static KeyBinding toggleAttack;
    public static KeyBinding toggleAll;
    public static KeyBinding toggleRecord;
    public static KeyBinding togglePlayback;

    public static void register() {
        toggleFish = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_fish",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.categories.aimod"
        ));

        toggleAttack = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_attack",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "key.categories.aimod"
        ));

        toggleAll = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_all",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_SEMICOLON,
                "key.categories.aimod"
        ));

        toggleRecord = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_record",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.categories.aimod"
        ));

        togglePlayback = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_playback",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "key.categories.aimod"
        ));
    }
}
