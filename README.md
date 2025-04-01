# Urchin Extras

A Minecraft 1.8.9 Forge mod that integrates with the Urchin API to display player tags in-game.

## Features

- Automatically checks player tags when joining a game
- Command to manually check player tags: `/urchintags <player>`
- Configurable API key: `/urchinapikey <key>`
- Caches player data to reduce API calls
- Color-coded tags based on tag type
- Rate limiting to prevent API abuse

## Installation

1. Install Minecraft 1.8.9 with Forge
2. Download the latest release from the releases page
3. Place the .jar file in your `.minecraft/mods` folder
4. Launch Minecraft with Forge
5. Get your API key from [Urchin](https://discord.gg/urchin)
6. Use `/urchinapikey <your-key>` to set your API key

## Building

1. Clone the repository
2. Run `./gradlew build`
3. Find the compiled jar in `build/libs`

## License

This project is licensed under the MIT License - see the LICENSE file for details.
