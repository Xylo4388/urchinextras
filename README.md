# Urchin Extras

A Minecraft mod that enhances your gameplay experience by providing real-time player information and cheater detection through the Urchin API.

## Features

- Automatic player tag checking when players join the game
- Manual tag checking with `/urchintags <player>` command
- Color-coded tag display for different types of information:
  - Sniper detection (Dark Red)
  - Possible sniper (Red)
  - Legit sniper (Gold)
  - Confirmed cheater (Dark Purple)
  - Blatant cheater (Red)
  - Closet cheater (Gold)
  - Caution (Yellow)
  - Account information (Aqua)
  - General info (Gray)
- Player name correction and caching system
- Rate-limited API calls to prevent abuse
- Configurable API key through `/urchinapikey <key>` command

## Requirements

- Minecraft 1.8 with Forge
- Urchin [API key](https://discord.gg/urchin/)

## Installation

1. Download the latest release
2. Place the jar file in your Minecraft mods folder
3. Set your Urchin API key using `/urchinapikey <key>`

## Usage

The mod automatically checks player tags when:
- The game starts
- Players join the server

You can also manually check tags using:
```
/urchintags <player>
```
