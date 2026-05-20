# AI Mod for Minecraft 1.21.11 (Fabric)

An AI-powered automation mod using Ollama (local AI) for chat responses.

## Features

- **Auto-Fish** — Automatically casts, waits for bites, and reels in. Mouse jitters naturally while waiting.
- **Auto-Attack** — Smoothly rotates camera toward nearest hostile mob, attacks on cooldown. Walks back and crouches if displaced.
- **Player Detection** — Looks at nearby players, crouches once, says "yo" naturally.
- **Smart Chat Responses** — Reads chat mentions/DMs and responds using Ollama. Typing delay is realistic.
- **Command Handling** — Players can ask you to "say X", "spin", "jump", "crouch" and the mod performs the action then resumes.
- **Captcha Solving** — Detects text/math captchas automatically. Image captchas use LLaVA vision model.
- **Learning System** — Unknown chat situations go to Ollama. Response is saved and used instantly next time.
- **Suspicion Detection** — If a response triggers suspicious follow-ups, it asks Ollama for a better answer and learns from it.

## Keybinds

| Key | Action |
|-----|--------|
| `K` | Toggle Auto-Fish |
| `L` | Toggle Auto-Attack |
| `;` | Toggle Everything ON/OFF |
| `G` | Open AI Prompt GUI |
| `R` | Toggle Recording (keybind registered, feature in progress) |
| `P` | Toggle Playback (keybind registered, feature in progress) |

### AI Prompt GUI (`G`)
Press `G` in-game to open a prompt window with 8 text lines.
- Type any prompt across the lines — they're joined together before being sent to Ollama
- Press `Tab` to move between lines
- Press `Enter` or click **Send to AI** to send the prompt
- The AI response is typed into chat with realistic human delay
- Press `Esc` or **Cancel** to close without sending

## Requirements

### Minecraft Setup
1. Install **Fabric Loader 0.18.1** for Minecraft 1.21.11 from [fabricmc.net](https://fabricmc.net/use/installer/)
2. Download **Fabric API 0.139.5+1.21.11** from [Modrinth](https://modrinth.com/mod/fabric-api) and put it in your mods folder
3. Drop `aimod-1.0.0.jar` into your `.minecraft/mods` folder

### Ollama Setup (for AI chat)
1. Download Ollama from [ollama.com](https://ollama.com)
2. Open a terminal and run:
   ```
   ollama pull llama3.1:8b
   ollama pull llava
   ```
3. Ollama runs automatically in the background

## Building the Mod

### Prerequisites
- Java 21 JDK
- Internet connection (downloads Gradle and Minecraft mappings on first build)

### Steps

**Windows:**
```
cd minecraft-mod
gradlew.bat build
```

**Linux/Mac:**
```
cd minecraft-mod
chmod +x gradlew
./gradlew build
```

The compiled mod will be at: `build/libs/aimod-1.0.0.jar`

> **Note:** First build takes 5-15 minutes as it downloads Minecraft, Fabric mappings, and dependencies. Subsequent builds are fast.

## Config File

After first launch, a config file appears at:
`.minecraft/config/aimod.json`

```json
{
  "ollamaUrl": "http://localhost:11434",
  "chatModel": "llama3.1:8b",
  "visionModel": "llava",
  "playerDetectionRange": 16.0,
  "attackRange": 4.0,
  "typingSpeedMsPerChar": 60,
  "typingSpeedVarianceMs": 30,
  "debugMode": false
}
```

## Learned Responses

The mod saves what it learns to:
`.minecraft/aimod/learned_responses.json`

You can edit this file directly to add or fix responses.
Suspicious interactions are logged to:
`.minecraft/aimod/suspicious_interactions.json`

## Important Notes

- This mod is intended for **private/personal servers or singleplayer** only.
- Auto-attacking and AFK automation may violate public server rules.
- The AI features only work when Ollama is running in the background.
- If Ollama is not running, the mod falls back to built-in known responses only.
