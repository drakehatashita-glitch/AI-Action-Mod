package com.aibot.mod.gui;

import com.aibot.mod.ai.OllamaClient;
import com.aibot.mod.ai.TypingSimulator;
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

    private final List<TextFieldWidget> lines = new ArrayList<>();
    private ButtonWidget submitButton;
    private ButtonWidget clearButton;
    private ButtonWidget cancelButton;

    private String statusText = "";
    private boolean isThinking = false;
    private int focusedLine = 0;

    public PromptScreen() {
        super(Text.literal("AI Prompt"));
    }

    @Override
    protected void init() {
        lines.clear();

        int panelWidth = Math.min(width - PADDING * 2, 600);
        int panelX = (width - panelWidth) / 2;
        int startY = PADDING + 30;

        for (int i = 0; i < MAX_LINES; i++) {
            int y = startY + i * FIELD_SPACING;
            TextFieldWidget field = new TextFieldWidget(
                    textRenderer,
                    panelX,
                    y,
                    panelWidth,
                    FIELD_HEIGHT,
                    Text.empty()
            );
            field.setMaxLength(MAX_LINE_LENGTH);
            field.setPlaceholder(i == 0
                    ? Text.literal("Type your prompt here... (Tab for next line)")
                    : Text.literal("Line " + (i + 1)));

            final int lineIndex = i;
            field.setChangedListener(text -> onFieldChanged(lineIndex));

            lines.add(field);
            addDrawableChild(field);
        }

        if (!lines.isEmpty()) {
            lines.get(0).setFocused(true);
            setFocused(lines.get(0));
        }

        int buttonY = startY + MAX_LINES * FIELD_SPACING + 10;
        int btnW = 100;
        int btnSpacing = 10;
        int totalBtnWidth = btnW * 3 + btnSpacing * 2;
        int btnStartX = (width - totalBtnWidth) / 2;

        submitButton = ButtonWidget.builder(Text.literal("Send to AI"), btn -> onSubmit())
                .dimensions(btnStartX, buttonY, btnW, 20)
                .build();

        clearButton = ButtonWidget.builder(Text.literal("Clear"), btn -> onClear())
                .dimensions(btnStartX + btnW + btnSpacing, buttonY, btnW, 20)
                .build();

        cancelButton = ButtonWidget.builder(Text.literal("Cancel"), btn -> close())
                .dimensions(btnStartX + (btnW + btnSpacing) * 2, buttonY, btnW, 20)
                .build();

        addDrawableChild(submitButton);
        addDrawableChild(clearButton);
        addDrawableChild(cancelButton);
    }

    private void onFieldChanged(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= lines.size()) return;
        TextFieldWidget field = lines.get(lineIndex);
        String text = field.getText();

        if (text.endsWith("\t")) {
            field.setText(text.substring(0, text.length() - 1));
            focusNextLine(lineIndex);
        }
    }

    private void focusNextLine(int current) {
        int next = current + 1;
        if (next < lines.size()) {
            lines.get(current).setFocused(false);
            lines.get(next).setFocused(true);
            setFocused(lines.get(next));
            focusedLine = next;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).isFocused()) {
                    focusNextLine(i);
                    return true;
                }
            }
        }

        if (keyCode == 257 || keyCode == 335) {
            if (!isThinking) {
                onSubmit();
                return true;
            }
        }

        if (keyCode == 256) {
            close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSubmit() {
        if (isThinking) return;

        StringBuilder sb = new StringBuilder();
        for (TextFieldWidget field : lines) {
            String t = field.getText().trim();
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(t);
            }
        }

        String fullPrompt = sb.toString().trim();
        if (fullPrompt.isEmpty()) {
            statusText = "Please enter a prompt first.";
            return;
        }

        isThinking = true;
        statusText = "Thinking...";
        submitButton.active = false;

        String system = """
                You are a real Minecraft player. Keep responses short and casual like a real player would type them.
                Use common Minecraft slang. Never use emojis. Never be formal. Max 1-2 sentences.
                """;

        OllamaClient.askWithContext(fullPrompt, system).thenAccept(response -> {
            if (client == null) return;
            client.execute(() -> {
                if (response != null && !response.isBlank()) {
                    String clean = response.replaceAll("[\n\r]", " ").trim();
                    if (clean.length() > 256) clean = clean.substring(0, 256);
                    TypingSimulator.sendWithDelay(clean);
                    statusText = "Sent: " + (clean.length() > 40 ? clean.substring(0, 40) + "..." : clean);
                } else {
                    statusText = "No response from Ollama.";
                }
                isThinking = false;
                submitButton.active = true;
            });
        });
    }

    private void onClear() {
        for (TextFieldWidget field : lines) {
            field.setText("");
        }
        statusText = "";
        if (!lines.isEmpty()) {
            lines.get(0).setFocused(true);
            setFocused(lines.get(0));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(width - PADDING * 2, 600);
        int panelX = (width - panelWidth) / 2;
        int panelHeight = PADDING + 30 + MAX_LINES * FIELD_SPACING + 60;

        context.fill(panelX - 5, PADDING - 5, panelX + panelWidth + 5, panelHeight + 5, 0xCC000000);
        context.drawBorder(panelX - 5, PADDING - 5, panelWidth + 10, panelHeight + 10, 0xFF444466);

        context.drawCenteredTextWithShadow(textRenderer, "AI Prompt", width / 2, PADDING + 8, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("Press Tab to move between lines, Enter to send"), panelX, PADDING + 18, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);

        if (!statusText.isEmpty()) {
            int statusY = PADDING + 30 + MAX_LINES * FIELD_SPACING + 36;
            int color = isThinking ? 0xFFFF55 : (statusText.startsWith("Sent") ? 0x55FF55 : 0xFF5555);
            context.drawCenteredTextWithShadow(textRenderer, statusText, width / 2, statusY, color);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
