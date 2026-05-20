package com.aibot.mod.gui;

import com.aibot.mod.macro.AiPromptExecutor;
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
    private static final int MAX_LINES = 8;
    private static final int MAX_LINE_LENGTH = 500;

    private final AiPromptExecutor promptExecutor;
    private final List<TextFieldWidget> lines = new ArrayList<>();

    private ButtonWidget submitButton;
    private ButtonWidget clearButton;
    private ButtonWidget cancelButton;

    private String statusText = "";
    private boolean isSending = false;

    public PromptScreen(AiPromptExecutor promptExecutor) {
        super(Text.literal("AI Prompt"));
        this.promptExecutor = promptExecutor;
    }

    @Override
    protected void init() {
        lines.clear();

        int panelWidth = Math.min(width - PADDING * 2, 600);
        int panelX = (width - panelWidth) / 2;
        int startY = PADDING + 40;

        for (int i = 0; i < MAX_LINES; i++) {
            int y = startY + i * FIELD_SPACING;
            TextFieldWidget field = new TextFieldWidget(
                    textRenderer, panelX, y, panelWidth, FIELD_HEIGHT, Text.empty()
            );
            field.setMaxLength(MAX_LINE_LENGTH);
            field.setPlaceholder(i == 0
                    ? Text.literal("Describe what you want to do... (Tab = next line, Enter = send)")
                    : Text.literal("Continue here..."));

            final int lineIndex = i;
            field.setChangedListener(text -> {
                if (text.endsWith("\t")) {
                    field.setText(text.substring(0, text.length() - 1));
                    focusLine(lineIndex + 1);
                }
            });

            lines.add(field);
            addDrawableChild(field);
        }

        if (!lines.isEmpty()) {
            setFocused(lines.get(0));
            lines.get(0).setFocused(true);
        }

        int buttonY = startY + MAX_LINES * FIELD_SPACING + 10;
        int btnW = 110;
        int gap = 8;
        int totalW = btnW * 3 + gap * 2;
        int bx = (width - totalW) / 2;

        submitButton = ButtonWidget.builder(Text.literal("Send to AI \u25B6"), b -> onSubmit())
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).isFocused()) { focusLine(i + 1); return true; }
            }
        }
        if ((keyCode == 257 || keyCode == 335) && !isSending) {
            onSubmit(); return true;
        }
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSubmit() {
        if (isSending) return;

        StringBuilder sb = new StringBuilder();
        for (TextFieldWidget f : lines) {
            String t = f.getText().trim();
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(t);
            }
        }

        String prompt = sb.toString().trim();
        if (prompt.isEmpty()) {
            statusText = "Write what you want the AI to do first.";
            return;
        }

        isSending = true;
        statusText = "Sending to AI...";
        submitButton.active = false;

        promptExecutor.executePrompt(prompt);

        if (client != null) {
            client.execute(() -> {
                statusText = "Executing! Close this window anytime.";
                isSending = false;
                submitButton.active = true;
            });
        }

        close();
    }

    private void onClear() {
        for (TextFieldWidget f : lines) f.setText("");
        statusText = "";
        focusLine(0);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(width - PADDING * 2, 600);
        int panelX = (width - panelWidth) / 2;
        int startY = PADDING + 40;
        int panelBottom = startY + MAX_LINES * FIELD_SPACING + 42;

        context.fill(panelX - 6, PADDING - 6, panelX + panelWidth + 6, panelBottom + 6, 0xBB000000);
        context.fill(panelX - 5, PADDING - 5, panelX + panelWidth + 5, panelBottom + 5, 0xFF1A1A2E);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("\u00A7bAI Prompt \u2014 Describe what to do"), width / 2, PADDING + 6, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("\u00A77Lines are joined together and sent as one prompt"),
                width / 2, PADDING + 18, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);

        if (!statusText.isEmpty()) {
            int statusY = startY + MAX_LINES * FIELD_SPACING + 34;
            int col = statusText.startsWith("Executing") ? 0x55FF55
                    : statusText.startsWith("Sending") ? 0xFFFF55
                    : 0xFF5555;
            context.drawCenteredTextWithShadow(textRenderer, statusText, width / 2, statusY, col);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
