# AiMod — Complete Setup & Usage Guide

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft Java Edition | **1.21.1** |
| Fabric Loader | **0.18.1 or newer** |
| Fabric API | **0.139.5+1.21.11 or newer** |
| Java | **21** |
| Ollama (optional, for AI chat) | Latest |

---

## Installation

### Step 1 — Install Fabric Loader

1. Go to **https://fabricmc.net/use/installer**
2. Download and run the installer
3. Select Minecraft version **1.21.1**
4. Click **Install**
5. Open the Minecraft launcher — a new profile called **fabric-loader-1.21.1** will appear

### Step 2 — Install Fabric API

1. Go to **https://modrinth.com/mod/fabric-api** and download the version for **1.21.1**
2. Place the downloaded `.jar` into your `mods` folder:
   - **Windows:** `%APPDATA%\.minecraft\mods\`
   - **macOS:** `~/Library/Application Support/minecraft/mods/`
   - **Linux:** `~/.minecraft/mods/`

### Step 3 — Install AiMod

1. Extract `aimod-1.0.0.tar.gz` — you'll find `aimod-1.0.0.jar` inside
2. Place `aimod-1.0.0.jar` into the same `mods` folder as above
3. Launch Minecraft using the **Fabric** profile

> You should see `[AI] Feature manager initialized` in chat when you join a world.

---

## Optional — Install Ollama (AI Chat)

Ollama runs a local AI model that powers smart chat responses. Without it, AiMod falls back to its built-in response library.

1. Download from **https://ollama.com**
2. Install and run it — it starts a local server at `http://localhost:11434`
3. Pull a model (recommended for low-end PCs):
   ```
   ollama pull llama3.2:3b
   ```
   Or for better responses on higher-end PCs:
   ```
   ollama pull llama3.1:8b
   ```
4. Ollama runs automatically in the background. AiMod will detect it on startup.

---

## Configuration File

After first launch, a config file is created at:
```
.minecraft/aimod/config.json
```

You can edit it while Minecraft is closed. All options:

```json
{
  "ollamaUrl": "http://localhost:11434",
  "ollamaModel": "llama3.2:3b",
  "ollamaTimeoutSeconds": 8,
  "temperature": 0.7,
  "maxTokens": 120,
  "attackRange": 4.5,
  "fishingCastDelay": 5,
  "chatResponseChance": 0.65,
  "chatCooldownTicks": 80,
  "conversationHistorySize": 4,
  "antiAfkEnabled": true,
  "antiAfkIntervalTicks": 2400,
  "sellInventoryThreshold": 27,
  "sellCommand": "/sell all",
  "greetingChance": 0.7,
  "typoChance": 0.03,
  "debugMode": false
}
```

| Option | What it does |
|---|---|
| `ollamaUrl` | Address of your Ollama server |
| `ollamaModel` | Which AI model to use |
| `ollamaTimeoutSeconds` | How long to wait for AI before falling back to built-in responses |
| `temperature` | AI creativity (0.0 = robotic, 1.0 = random) |
| `maxTokens` | Max length of AI chat replies |
| `attackRange` | How far away enemies can be before auto-attack targets them |
| `fishingCastDelay` | Ticks to wait before re-casting rod |
| `chatResponseChance` | Probability (0–1) of responding to any given chat message |
| `chatCooldownTicks` | Minimum ticks between chat responses (20 ticks = 1 second) |
| `conversationHistorySize` | How many past messages the AI remembers per conversation |
| `antiAfkEnabled` | Subtle random movement to prevent AFK kicks |
| `antiAfkIntervalTicks` | How often the anti-AFK nudge fires (2400 = 2 minutes) |
| `sellInventoryThreshold` | Auto-sell triggers when this many slots are filled (max 36) |
| `sellCommand` | Command to run when auto-selling |
| `greetingChance` | Probability of greeting a player when they join or come near |
| `typoChance` | Probability of a realistic typo in each chat message (0.03 = 3%) |
| `debugMode` | Shows extra action bar messages for debugging |

---

## Keybinds

All bindings are in **Options → Controls → Miscellaneous**.

| Key | Action |
|---|---|
| **K** | Toggle Auto-Fish on/off |
| **L** | Toggle Auto-Attack on/off |
| **;** | Toggle ALL features on/off at once |
| **R** | Start / Stop recording a macro |
| **P** | Play / Stop the last recorded macro |
| **G** | Open the AI Prompt window (type a task for the AI to perform) |
| **J** | Open the Recording Manager (manage saved macros) |

---

## Feature Guide

---

### Auto-Fish

Press **K** to toggle.

- Automatically casts your fishing rod when held
- Detects bite by watching the bobber dip below the water surface (not just the hook sound)
- Reels in at the right moment for maximum XP and loot
- Checks rod durability — warns you when it gets low
- Between casts, the bot looks around naturally (idle phases) to appear human
- Make sure you are **holding a fishing rod** and **standing near water**

**Tips:**
- Works best in a 3×3 or larger body of water
- The rod must be in your **main hand**
- AFK fish farms (with trap doors) work normally

---

### Auto-Attack

Press **L** to toggle.

- Targets the nearest hostile mob within `attackRange` blocks
- **Prioritises low-health enemies** to finish fights faster
- **Strafe behaviour** — circles the target rather than standing still
- **W-tap rhythm** — briefly releases forward key before each hit to improve crit rate
- **Retreat logic** — if your health drops below 25%, the bot runs away until you recover to 50%
- **Sprint-crit** management — sprints and times attacks for critical hits

**Tips:**
- Equip a sword or axe in your main hand for best results
- Works in combination with macros (record a patrol path then enable auto-attack)
- Disable if building or farming to avoid accidental hits

---

### Auto-Sell

Runs automatically when your inventory reaches `sellInventoryThreshold` filled slots.

- Runs the configured `sellCommand` (default: `/sell all`)
- Tries 6 different fallback sell commands if the first fails
- Targets common sellable items: wheat, carrots, potatoes, beetroot, sugarcane, iron, gold, coal, diamonds, and more
- Tracks how many sell operations it has done this session

**To configure:**
Edit `config.json` and set `sellCommand` to whatever your server uses, e.g. `/shop sell all` or `/market sell`.

---

### Chat AI

Runs automatically when other players chat near you.

- **Built-in responses** — 80+ trigger phrases with 10+ varied replies each (greetings, questions, server talk, farming, pvp, etc.)
- **Fuzzy matching** — catches typos and alternate phrasings
- **Ollama AI** — if Ollama is running, uses the AI model for anything it doesn't recognise
- **Conversation history** — remembers the last few messages for context
- **Rate limiting** — won't spam; enforces cooldown between replies
- **Spam detection** — ignores repeated identical messages
- **Realistic typing delay** — types at human speed with occasional typos
- **`wave` command** — if someone says `wave` to you, the bot waves back in chat

**How it decides to reply:**
1. Checks if the message matches a learned trigger (fast, no AI needed)
2. If not, sends to Ollama with conversation context (if available)
3. Falls back to a generic built-in response if Ollama times out
4. Only replies with `chatResponseChance` probability (default 65%) to avoid being too responsive

---

### Player Detection

Runs automatically.

- Detects when a player comes within range
- Sends a randomised greeting (controlled by `greetingChance`)
- Tracks **suspicion levels** — if a player keeps watching you, it knows
- Remembers returning players and greets them differently
- Forgets players after 5 minutes of absence

---

### Captcha Solver

Runs automatically.

- Watches chat for captcha challenges from anti-bot plugins
- Solves:
  - **Word math** — "what is two plus three?" → answers `5`
  - **Number math** — "solve: 12 + 7" → answers `19`
  - **Roman numeral math** — "IV + III" → answers `7`
  - **`/captcha <answer>`** pattern — runs the command automatically
- Filters out false positives (common words that accidentally match)

No setup required — it runs silently in the background.

---

### Macro Recording & Playback

**Recording a macro:**
1. Press **R** to start recording
2. Play normally — walk, look around, chat, run commands, switch hotbar slots, drop items, swap hands, use items — everything is captured
3. Press **R** again to stop — a save dialog appears where you can name the recording
4. The recording is saved to `.minecraft/aimod/recordings/<name>.json`

**What gets recorded:**
- Movement keys (WASD, jump, sneak, sprint)
- Mouse look (yaw and pitch)
- Left click (attack) and right click (use/place)
- Hotbar slot changes (1–9 keys)
- Drop item (Q)
- Swap hands (F)
- Pick block (middle click)
- Chat messages you send
- Commands you run (e.g. `/home`, `/warp farm`)

**Playing back a macro:**
1. Press **P** to play the last saved recording on loop
2. Press **P** again to stop
3. Use **J** (Recording Manager) to play any named recording

**Playback features:**
- **Gaussian noise** — adds tiny random variation to mouse movements each loop so it never looks identical
- **Timing drift** — slightly varies the speed of each loop (±12%)
- **Human breaks** — after ~12 loops, takes a random 3–12 second pause
- **Teleport detection** — if you get teleported mid-playback, enters a "look around confused" scan mode instead of walking into a wall
- **Mouse lock mode** — available via the AI prompt (`/G`) for exact pixel-perfect replays

---

### AI Prompt Executor

Press **G** to open the prompt window.

Type a natural language task and the AI will convert it into a sequence of actions. Examples:

| You type | Bot does |
|---|---|
| `walk forward for 3 seconds` | Holds W for 60 ticks |
| `look left and sprint for 2 seconds` | Rotates yaw, holds sprint+forward |
| `jump 5 times` | Presses jump 5 times with pauses |
| `sprint jump forward` | Sprint-jumps forward |
| `circle strafe` | Circles strafes for a set duration |

**Requires Ollama to be running.** Without Ollama the prompt window will still open but commands won't execute.

---

### Recording Manager

Press **J** to open.

- Lists all saved recordings
- Shows frame count and file size for each
- Lets you **rename**, **delete**, or **play** any recording
- Play button starts looped playback immediately

---

### Anti-AFK

Runs automatically when no other features are active.

- Every `antiAfkIntervalTicks` ticks (default: 2 minutes), makes a tiny random camera nudge
- Invisible to other players, just enough to prevent server AFK kicks
- Automatically disabled when Auto-Fish, Auto-Attack, or Playback is running

Disable it in config: `"antiAfkEnabled": false`

---

## Recordings Folder

All macro recordings are stored as JSON files:
```
.minecraft/aimod/recordings/
  last.json          ← most recent recording
  farm_route.json    ← named recordings
  sell_run.json
  ...
```

You can back these up, share them, or copy them between computers.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Mod not loading | Make sure Fabric API is installed alongside the mod |
| Wrong Minecraft version | AiMod requires exactly **1.21.1** — check your launcher profile |
| AI chat not working | Check Ollama is running: open a browser and go to `http://localhost:11434` — you should see "Ollama is running" |
| AI responses are slow | Reduce `ollamaTimeoutSeconds` in config so it falls back faster, or use a smaller model like `llama3.2:1b` |
| Auto-fish not casting | Make sure a fishing rod is in your **main hand**, not offhand |
| Macro plays too fast/slow | This is normal — timing drift is intentional. Set `mouseLocked` via AI prompt if you need exact timing |
| Commands not replaying | Make sure you typed the command while recording was active (the `[AI] Recording started` message must have appeared first) |
| `selectedSlot` error on older builds | Use the compiled jar from this release — this was fixed |
