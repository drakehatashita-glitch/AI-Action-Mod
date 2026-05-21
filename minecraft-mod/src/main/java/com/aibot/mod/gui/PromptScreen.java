package com.aibot.mod.gui;

import com.aibot.mod.macro.AiPromptExecutor;
import com.aibot.mod.macro.MovementRecorder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class PromptScreen extends Screen {

    private static final int PADDING = 20;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_SPACING = 24;
    private static final int MAX_LINES = 7;
    private static final int MAX_LINE_LENGTH = 500;

    private final AiPromptExecutor promptExecutor;
    private final MovementRecorder recorder;
    private final boolean postRecording;

    private TextFieldWidget nameField = null;
    private final List<TextFieldWidget> lines = new ArrayList<>();

    private ButtonWidget submitButton;
    private ButtonWidget clearButton;
    private ButtonWidget cancelButton;

    private String statusText = "";

    public PromptScreen(AiPromptExecutor promptExecutor) {
        this(promptExecutor, null, false);
    }

    public PromptScreen(AiPromptExecutor promptExecutor, MovementRecorder recorder, boolean postRecording) {
        super(Text.literal(postRecording ? "Save & Describe Recording" : "AI Prompt"));
        this.promptExecutor = promptExecutor;
        this.recorder = recorder;
        this.postRecording = postRecording;
    }

    @Override
    protected void init() {
        lines.clear();
        nameField = null;

        int panelWidth = Math.min(width - PADDING * 2, 600);
        int panelX = (width - panelWidth) / 2;
        int currentY = PADDING + 42;

        if (postRecording) {
            nameField = new TextFieldWidget(textRenderer, panelX, currentY, panelWidth, FIELD_HEIGHT, Text.empty());
            nameField.setMaxLength(40);
            nameField.setPlaceholder(Text.literal("Recording name (e.g. farming, fishing, combat)"));
            addDrawableChild(nameField);
            currentY += FIELD_SPACING + 6;
        }

        int promptStartY = currentY;

        for (int i = 0; i < MAX_LINES; i++) {
            int y = promptStartY + i * FIELD_SPACING;
            TextFieldWidget field = new TextFieldWidget(
                    textRenderer, panelX, y, panelWidth, FIELD_HEIGHT, Text.empty()
            );
            field.setMaxLength(MAX_LINE_LENGTH);
            if (i == 0) {
                field.setPlaceholder(postRecording
                        ? Text.literal("Describe extra behaviour (optional, press Enter to just save)")
                        : Text.literal("Describe what you want the AI to do... (Tab = next line)"));
            } else {
                field.setPlaceholder(Text.literal("Continue here..."));
            }

            final int idx = i;
            field.setChangedListener(text -> {
                if (text.endsWith("\t")) {
                    field.setText(text.substring(0, text.length() - 1));
                    focusLine(idx + 1);
                }
            });

            lines.add(field);
            addDrawableChild(field);
        }

        if (postRecording && nameField != null) {
            setFocused(nameField);
            nameField.setFocused(true);
        } else if (!lines.isEmpty()) {
            setFocused(lines.get(0));
            lines.get(0).setFocused(true);
        }

        int buttonY = promptStartY + MAX_LINES * FIELD_SPACING + 10;
        int btnW = 110;
        int gap = 8;
        int totalW = btnW * 3 + gap * 2;
        int bx = (width - totalW) / 2;

        String submitLabel = postRecording ? "Save & Run AI \u25B6" : "Send to AI \u25B6";
        submitButton = ButtonWidget.builder(Text.literal(submitLabel), b -> onSubmit())
                .dimensions(bx, buttonY, btnW, 20).build();
        clearButton = ButtonWidget.builder(Text.literal("Clear"), b -> onClear())
                .dimensions(bx + btnW + gap, buttonY, btnW, 20).build();
        cancelButton = ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(bx + (btnW + gap) * 2, buttonY, btnW, 20).build();

        addDrawableChild(submitButton);
        addDrawableChild(clearButton);
        addDrawableChild(cancelButton);
    }

    private void focusLine(int index) {
        if (index < 0 || index >= lines.size()) return;
        for (TextFieldWidget f : lines) f.setFocused(false);
        lines.get(index).setFocused(true);
        setFocused(lines.get(index));
    }

    private void onSubmit() {
        StringBuilder sb = new StringBuilder();
        for (TextFieldWidget f : lines) {
            String t = f.getText().trim();
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(t);
            }
        }
        String prompt = sb.toString().trim();

        if (postRecording) {
            String recName = (nameField != null && !nameField.getText().isBlank())
                    ? nameField.getText().trim()
                    : "recording_" + System.currentTimeMillis();

            if (recorder != null) {
                recorder.save(recName);
            }

            if (!prompt.isEmpty()) {
                promptExecutor.executeAndSave(prompt, null);
            }

            close();
            return;
        }

        if (prompt.isEmpty()) {
            statusText = "Write what you want the AI to do first.";
            return;
        }

        promptExecutor.executePrompt(prompt);
        close();
    }

    private void onClear() {
        for (TextFieldWidget f : lines) f.setText("");
        if (nameField != null) nameField.setText("");
        statusText = "";
        if (postRecording && nameField != null) {
            setFocused(nameField);
            nameField.setFocused(true);
        } else {
            focusLine(0);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(width - PADDING * 2, 600);
        int panelX = (width - panelWidth) / 2;
        int nameExtra = postRecording ? FIELD_SPACING + 6 : 0;
        int promptStartY = PADDING + 42 + nameExtra;
        int panelTop = PADDING - 6;
        int panelBottom = promptStartY + MAX_LINES * FIELD_SPACING + 42;

        context.fill(panelX - 6, panelTop, panelX + panelWidth + 6, panelBottom, 0xBB000000);
        context.fill(panelX - 5, panelTop + 1, panelX + panelWidth + 5, panelBottom - 1, 0xFF1A1A2E);

        String title = postRecording
                ? "\u00A7bSave Recording \u2014 Name it & describe behaviour"
                : "\u00A7bAI Prompt \u2014 Describe what to do";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(title), width / 2, PADDING + 6, 0xFFFFFF);

        String hint = postRecording
                ? "\u00A77Tab to move to prompt, Enter to save. Prompt is optional."
                : "\u00A77Lines are joined and sent as one prompt to the AI";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), width / 2, PADDING + 18, 0xAAAAAA);

        if (postRecording) {
            context.drawTextWithShadow(textRenderer, Text.literal("\u00A7eName:"),
                    panelX, PADDING + 42 - 10, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, statusText, width / 2, panelBottom - 14, 0x55FF55);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
