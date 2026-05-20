package com.aibot.mod.macro;

public class ActionFrame {
    // --- Movement state (continuous, per-tick) ---
    public boolean forward;
    public boolean backward;
    public boolean left;
    public boolean right;
    public boolean jumping;
    public boolean sneaking;
    public boolean sprinting;
    public boolean attacking;
    public boolean using;
    public float yaw;
    public float pitch;
    public int tickDuration = 1;

    // --- Hotbar slot (0-8; -1 = no change this frame) ---
    public int hotbarSlot = -1;

    // --- One-tick event actions (pressed this frame only) ---
    public boolean dropItem;      // Q — drop one item from hand
    public boolean swapHands;     // F — swap main/offhand
    public boolean pickBlock;     // middle click — pick block

    // --- Chat / command event (null = none this frame) ---
    // Commands are stored with their leading '/' included.
    public String chatMessage = null;

    public ActionFrame() {}

    public ActionFrame copy() {
        ActionFrame f = new ActionFrame();
        f.forward    = forward;
        f.backward   = backward;
        f.left       = left;
        f.right      = right;
        f.jumping    = jumping;
        f.sneaking   = sneaking;
        f.sprinting  = sprinting;
        f.attacking  = attacking;
        f.using      = using;
        f.yaw        = yaw;
        f.pitch      = pitch;
        f.tickDuration = tickDuration;
        f.hotbarSlot = hotbarSlot;
        f.dropItem   = dropItem;
        f.swapHands  = swapHands;
        f.pickBlock  = pickBlock;
        f.chatMessage = chatMessage;
        return f;
    }

    /** Returns true if this frame carries any one-tick event that must not be compressed away. */
    public boolean hasEvent() {
        return chatMessage != null || dropItem || swapHands || pickBlock;
    }
}
