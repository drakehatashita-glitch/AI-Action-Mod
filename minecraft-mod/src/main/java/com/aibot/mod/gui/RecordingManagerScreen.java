package com.aibot.mod.gui;

import com.aibot.mod.macro.MovementPlayback;
import com.aibot.mod.macro.MovementRecorder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class RecordingManagerScreen extends Screen {

    private final MovementRecorder recorder;
    private final MovementPlayback playback;

    private List<String> recordings = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 10;
    private static final int LIST_X_PAD = 20;

    private String statusText = "";

    public RecordingManagerScreen(MovementRecorder recorder, MovementPlayback playback) {
        super(Text.literal("Recordings"));
        this.recorder = recorder;
        this.playback = playback;
    }

    @Override
    protected void init() {
        recordings = new ArrayList<>(recorder.listRecordings());

        int closeX = width / 2 - 50;
        int closeY = height - 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(closeX, closeY, 100, 20).build());

        buildRows();
    }

    private void buildRows() {
        clearChildren();

        recordings = new ArrayList<>(recorder.listRecordings());

        int closeX = width / 2 - 50;
        int closeY = height - 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(closeX, closeY, 100, 20).build());

        int listX = LIST_X_PAD;
        int listY = 50;
        int panelWidth = width - LIST_X_PAD * 2;
        int playBtnW = 70;
        int deleteBtnW = 60;
        int gap = 6;

        int start = scrollOffset;
        int end = Math.min(recordings.size(), scrollOffset + VISIBLE_ROWS);

        for (int i = start; i < end; i++) {
            String name = recordings.get(i);
            int rowY = listY + (i - start) * ROW_HEIGHT;
            final String finalName = name;

            int deleteX = listX + panelWidth - deleteBtnW;
            int playX = deleteX - playBtnW - gap;

            addDrawableChild(ButtonWidget.builder(Text.literal("\u25B6 Play"), b -> playRecording(finalName))
                    .dimensions(playX, rowY, playBtnW, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("\u2716 Delete"), b -> {
                recorder.delete(finalName);
                statusText = "Deleted: " + finalName;
                buildRows();
            }).dimensions(deleteX, rowY, deleteBtnW, 18).build());
        }

        if (scrollOffset > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("\u25B2 Up"), b -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
                buildRows();
            }).dimensions(width / 2 - 25, 28, 50, 16).build());
        }

        if (scrollOffset + VISIBLE_ROWS < recordings.size()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("\u25BC Down"), b -> {
                scrollOffset++;
                buildRows();
            }).dimensions(width / 2 - 25, height - 52, 50, 16).build());
        }
    }

    private void playRecording(String name) {
        var frames = recorder.load(name);
        if (frames == null || frames.isEmpty()) {
            statusText = "Could not load: " + name;
            return;
        }
        if (client == null) return;
        playback.setMouseLocked(false);
        playback.start(frames, true);
        statusText = "Playing: " + name;
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = width - LIST_X_PAD * 2;
        int listY = 50;

        context.fill(LIST_X_PAD - 4, 40, LIST_X_PAD + panelWidth + 4, listY + VISIBLE_ROWS * ROW_HEIGHT + 4, 0xBB000000);
        context.drawCenteredTextWithShadow(textRenderer, "\u00A7bSaved Recordings", width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("\u00A77" + recordings.size() + " recording(s) found"), width / 2, 22, 0xAAAAAA);

        if (recordings.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("\u00A77No recordings yet. Press [R] to record."),
                width / 2, listY + 40, 0xAAAAAA);
        } else {
            int start = scrollOffset;
            int end = Math.min(recordings.size(), scrollOffset + VISIBLE_ROWS);
            for (int i = start; i < end; i++) {
                String name = recordings.get(i);
                int rowY = listY + (i - start) * ROW_HEIGHT;
                if (i % 2 == 0) {
                    context.fill(LIST_X_PAD - 2, rowY - 1, LIST_X_PAD + panelWidth + 2, rowY + ROW_HEIGHT - 2, 0x22FFFFFF);
                }
                context.drawTextWithShadow(textRenderer, Text.literal("\u00A7f" + name),
                    LIST_X_PAD + 4, rowY + 4, 0xFFFFFF);
            }
        }

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, statusText, width / 2, height - 50, 0x55FF55);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
