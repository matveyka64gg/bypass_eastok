# FunTime Live

**FunTime Live** is a local Windows control panel for interactive Minecraft streams. It receives one webhook from EasTok, maps TikTok gifts to Minecraft effects, and sends the selected effect to a Paper server through RCON.

No public server, domain name, port forwarding, or cloud account is required. EasTok and FunTime Live communicate on the same PC through `127.0.0.1`.

[Русская инструкция](README.ru.md)

## What it does

- Runs a local HTTP webhook at `http://127.0.0.1:4782/eastok/gift`.
- Receives the viewer name and gift name from EasTok.
- Maps each gift to a configurable FunTime effect.
- Sends one RCON command to a Paper server.
- Shows an animated dark control panel with connection status and an activity feed.
- Includes a local gift test, so effects can be checked without starting a TikTok LIVE.
- Stores the server path and the gift map locally. Personal configuration is ignored by Git.

## Architecture

```text
TikTok LIVE
    -> EasTok "Any gift" trigger
    -> HTTP webhook on 127.0.0.1
    -> FunTime Live gift map
    -> RCON command
    -> Paper + FunTimeItems plugin
```

## Requirements

- Windows 10 or Windows 11.
- Java 21 or newer. Java 25 is recommended for Minecraft 1.21.11.
- A running Paper server with RCON enabled.
- The companion `FunTimeItems.jar` plugin installed in the Paper `plugins` folder.
- EasTok connected to TikTok LIVE.

## Release Quick Start

1. Place `FunTimeLive.jar` and `FunTimeLive.bat` in the same folder.
2. Start the Paper server.
3. Double-click `FunTimeLive.bat`.
4. In the app, check the `SERVER.PROPERTIES` path. It should point to your server's `server.properties` file.
5. Click `Reload server`, then `Test RCON`. The status must become `RCON: connected`.
6. Configure EasTok as described below.

The application reads `rcon.password` and `rcon.port` from `server.properties` locally. It does not put the password into the UI or the activity log.

## Paper Server Setup

Install the companion plugin:

```text
<your Paper server>/plugins/FunTimeItems.jar
```

Then fully restart Paper. Do not use `/reload` for this plugin.

Ensure the following settings exist in `server.properties`:

```properties
enable-rcon=true
rcon.port=25575
rcon.password=use_a_long_private_password
```

## EasTok Setup

Create **one** `Any gift` trigger in EasTok. Add the `HTTP Webhook` action and use:

| Field | Value |
| --- | --- |
| URL | `http://127.0.0.1:4782/eastok/gift` |
| Method | `POST` |
| JSON body | `{"user":"{nickname}","gift":"{gift_name}"}` |
| JSON headers | `{"Content-Type":"application/json"}` |

EasTok's own Test button sends template text such as `{gift_name}`, not a real TikTok gift. FunTime Live deliberately accepts that request with `200 OK` but does not run a Minecraft effect. Use the app's **Local gift test** area to test a real effect, for example `Donut`.

## TikTok Chat in Minecraft

Create a second EasTok trigger for TikTok chat messages. Add another `HTTP Webhook` action:

| Field | Value |
| --- | --- |
| URL | `http://127.0.0.1:4782/eastok/chat` |
| Method | `POST` |
| JSON body | `{"user":"{nickname}","message":"{message}"}` |
| JSON headers | `{"Content-Type":"application/json"}` |

Incoming messages appear in Minecraft as ` [TikTok] viewer » message `. FunTime Live caps chat messages at 240 characters and strips color/control codes before sending them to Paper.

## Gift Map

The left panel contains one mapping per line:

```text
Heart Me=heal
GG=diamond
Coffee=speed
Ice Cream Cone=freeze
Rose=rose
Creeper=creeper
Wave Firework=fireworks
Choc Chip Cookie=animal
Doughnut=donut
Single Strike=rocket
Little Crown=crusher
Fairy Hide=ghost
Sunglasses=blind
Whale Diving=trap
Cheer Mic=raid
Triple Thunder=tornado
Meteor Shower=meteor
Fiery Dragon=dragon
Viking Hammer=wither
Wolf=wolves
TikTok Universe=chaos
```

The left side is the gift name received from TikTok/EasTok. The right side is a FunTime effect id. Gift matching ignores case and supports partial names, so `ice cream cone` matches `ice cream`.

Common effect ids:

```text
rose, donut, heal, diamond, speed, freeze, blind, raid, animal,
creeper, rocket, crusher, trap, dragon, meteor, chaos, tornado,
fireworks, wolves, loot, ghost, wither
```

Save the map after editing it. FunTime Live creates `FunTimeLive-gifts.properties` next to the application; this personal file is ignored by Git.

## Testing

1. Start Paper and FunTime Live.
2. Click `Test RCON`.
3. Enter a gift name in **Local gift test**, for example `Doughnut`.
4. Enter any viewer name and click `Run effect`.
5. Check the Activity Feed for the routed effect and the Paper response.

## Troubleshooting

| Problem | Check |
| --- | --- |
| `Webhook failed: Address already in use` | Close another FunTime Live instance, then start the app again. |
| `RCON: failed` | Start Paper, check `enable-rcon=true`, verify the server path, then click `Reload server`. |
| EasTok test has no Minecraft effect | Expected. The EasTok Test button sends placeholders, not a live gift name. |
| A live gift gives fallback loot | Add its exact name to the gift map, save it, then test again. |
| Plugin command is unknown | Install the current `FunTimeItems.jar` and fully restart Paper. |

## Build From Source

```bat
build.bat
run.bat
```

The project uses only the Java standard library and `jdk.httpserver`; no external dependency installation is required.

## Privacy and Security

- The webhook binds only to `127.0.0.1`, not to your network or the internet.
- RCON credentials remain in the Paper `server.properties` file.
- Do not commit `FunTimeLive.properties` or `FunTimeLive-gifts.properties`.
- Never expose the RCON port to the public internet.
