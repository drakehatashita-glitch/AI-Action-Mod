package com.aibot.mod.macro;

public class ActionFrame {
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

    public ActionFrame() {}

    public ActionFrame copy() {
        ActionFrame f = new ActionFrame();
        f.forward = forward;
        f.backward = backward;
        f.left = left;
        f.right = right;
        f.jumping = jumping;
        f.sneaking = sneaking;
        f.sprinting = sprinting;
        f.attacking = attacking;
        f.using = using;
        f.yaw = yaw;
        f.pitch = pitch;
        f.tickDuration = tickDuration;
        return f;
    }
}
