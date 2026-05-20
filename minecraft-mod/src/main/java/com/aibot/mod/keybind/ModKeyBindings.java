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
    public static KeyBinding openPromptGui;
    public static KeyBinding openRecordingManager;

    public static void register() {
        toggleFish = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_fish", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyBinding.Category.MISC
        ));
        toggleAttack = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_attack", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, KeyBinding.Category.MISC
        ));
        toggleAll = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_all", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, KeyBinding.Category.MISC
        ));
        toggleRecord = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_record", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, KeyBinding.Category.MISC
        ));
        togglePlayback = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.toggle_playback", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, KeyBinding.Category.MISC
        ));
        openPromptGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.open_prompt_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, KeyBinding.Category.MISC
        ));
        openRecordingManager = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimod.open_recording_manager", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, KeyBinding.Category.MISC
        ));
    }
}
