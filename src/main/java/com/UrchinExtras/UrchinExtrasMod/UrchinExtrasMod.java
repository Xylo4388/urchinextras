package com.UrchinExtras.UrchinExtrasMod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ClientCommandHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod(modid = UrchinExtrasMod.MODID, version = UrchinExtrasMod.VERSION, name = UrchinExtrasMod.NAME)
public class UrchinExtrasMod {
    public static final String MODID = "urchinextras";
    public static final String VERSION = "1.0";
    public static final String NAME = "Urchin Extras";
    
    private static String apiKey;
    private static Configuration config;
    private static final Gson gson = new Gson();
    private static final JsonParser jsonParser = new JsonParser();
    private static Timer timer;
    private static boolean waitingForWho = false;
    private static final Map<String, CachedPlayerData> playerCache = new ConcurrentHashMap<>();
    private static final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(5);
    private static final long RATE_LIMIT_DELAY = 1000; // 1 second between API calls
    private static long lastApiCall = 0;
    
    private static class CachedPlayerData {
        final List<String> tagStrings;
        final long timestamp;
        final String correctName;
        
        CachedPlayerData(List<String> tagStrings, String correctName) {
            this.tagStrings = tagStrings;
            this.timestamp = System.currentTimeMillis();
            this.correctName = correctName;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        apiKey = config.get("API", "apiKey", "", "Your Urchin API Key").getString();
        config.save();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        timer = new Timer();
        ClientCommandHandler.instance.registerCommand(new CheckTagsCommand());
        ClientCommandHandler.instance.registerCommand(new SetApiKeyCommand());
    }
    
    @EventHandler
    public void postInit(FMLInitializationEvent event) {
        // Shutdown hook for the executor service
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String message = event.message.getUnformattedText();
        
        // Handle the actual chat message format we're seeing
        if (message.contains("The game starts in 5 seconds")) {
            waitingForWho = true;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (waitingForWho) {
                        // Ensure command runs on client thread
                        Minecraft.getMinecraft().addScheduledTask(() -> {
                            Minecraft.getMinecraft().thePlayer.sendChatMessage("/who");
                        });
                    }
                }
            }, 6000);
        }
        
        // Handle both "Players online:" and "ONLINE:" formats
        if (message.contains("ONLINE:") || message.startsWith("Players online:")) {
            String playerList = message.contains("ONLINE:") ? 
                message.substring(message.indexOf("ONLINE:") + "ONLINE:".length()) :
                message.substring("Players online:".length());
            String[] players = playerList.trim().split(", ");
            for (String player : players) {
                if (!player.isEmpty()) {
                    checkPlayerTagsAsync(player.trim());
                }
            }
        }
    }
    
    private static void checkPlayerTagsAsync(String playerName) {
        executorService.submit(() -> {
            try {
                // First, get the correct capitalization
                String correctName = getCorrectPlayerName(playerName);
                if (correctName != null) {
                    checkPlayerTags(correctName);
                } else {
                    checkPlayerTags(playerName); // Fallback to original name
                }
            } catch (Exception e) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.RED + "[UrchinExtras] Error: " + e.getMessage()));
            }
        });
    }
    
    private static String getCorrectPlayerName(String playerName) {
        try {
            // First, get UUID from username
            URL uuidUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection uuidConn = (HttpURLConnection) uuidUrl.openConnection();
            uuidConn.setRequestMethod("GET");
            
            if (uuidConn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(uuidConn.getInputStream()));
                JsonObject response = jsonParser.parse(reader).getAsJsonObject();
                reader.close();
                
                String uuid = response.get("id").getAsString();
                String correctName = response.get("name").getAsString();
                
                return correctName;
            }
        } catch (Exception e) {
            // Silently fail and return null
        }
        return null;
    }
    
    private static void checkPlayerTags(String playerName) {
        if (apiKey.isEmpty()) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.RED + "[UrchinExtras] API key not set! Use /urchinapikey <key> to set it."));
            return;
        }

        // Check cache first
        CachedPlayerData cachedData = playerCache.get(playerName.toLowerCase());
        if (cachedData != null && !cachedData.isExpired()) {
            displayTagMessage(cachedData.correctName, cachedData.tagStrings);
            return;
        }

        // Rate limiting
        long currentTime = System.currentTimeMillis();
        long timeSinceLastCall = currentTime - lastApiCall;
        if (timeSinceLastCall < RATE_LIMIT_DELAY) {
            try {
                Thread.sleep(RATE_LIMIT_DELAY - timeSinceLastCall);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        lastApiCall = System.currentTimeMillis();

        try {
            URL url = new URL("https://urchin.ws/player/" + playerName + "?api_key=" + apiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.RED + "[UrchinExtras] API Error: " + conn.getResponseMessage()));
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            JsonObject jsonResponse = jsonParser.parse(response.toString()).getAsJsonObject();
            if (!jsonResponse.has("tags")) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.RED + "[UrchinExtras] Invalid API response: missing tags field"));
                return;
            }
            
            JsonArray tagsArray = jsonResponse.get("tags").getAsJsonArray();
            if (tagsArray.size() > 0) {
                List<String> tagStrings = new ArrayList<>();
                for (int i = 0; i < tagsArray.size(); i++) {
                    JsonObject tag = tagsArray.get(i).getAsJsonObject();
                    String type = tag.get("type").getAsString();
                    String reason = tag.has("reason") ? tag.get("reason").getAsString() : "No reason provided";
                    
                    // Preserve original tag type but capitalize first letter of each word
                    String[] words = type.split("_");
                    StringBuilder capitalizedType = new StringBuilder();
                    for (String word : words) {
                        if (capitalizedType.length() > 0) {
                            capitalizedType.append(" ");
                        }
                        if (!word.isEmpty()) {
                            capitalizedType.append(Character.toUpperCase(word.charAt(0)));
                            if (word.length() > 1) {
                                capitalizedType.append(word.substring(1).toLowerCase());
                            }
                        }
                    }
                    
                    // Color code based on tag type
                    String colorCode;
                    switch (type.toLowerCase()) {
                        case "sniper":
                            colorCode = EnumChatFormatting.DARK_RED.toString();
                            break;
                        case "possible_sniper":
                            colorCode = EnumChatFormatting.RED.toString();
                            break;
                        case "legit_sniper":
                            colorCode = EnumChatFormatting.GOLD.toString();
                            break;
                        case "confirmed_cheater":
                            colorCode = EnumChatFormatting.DARK_PURPLE.toString();
                            break;
                        case "blatant_cheater":
                            colorCode = EnumChatFormatting.RED.toString();
                            break;
                        case "closet_cheater":
                            colorCode = EnumChatFormatting.GOLD.toString();
                            break;
                        case "caution":
                            colorCode = EnumChatFormatting.YELLOW.toString();
                            break;
                        case "account":
                            colorCode = EnumChatFormatting.AQUA.toString();
                            break;
                        case "info":
                            colorCode = EnumChatFormatting.GRAY.toString();
                            break;
                        default:
                            colorCode = EnumChatFormatting.WHITE.toString();
                    }
                    
                    tagStrings.add(colorCode + capitalizedType.toString() + ": " + reason);
                }
                
                // Cache the results with correct name
                playerCache.put(playerName.toLowerCase(), new CachedPlayerData(tagStrings, playerName));
                displayTagMessage(playerName, tagStrings);
            }
        } catch (Exception e) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.RED + "[UrchinExtras] Error: " + e.getMessage()));
        }
    }
    
    private static void displayTagMessage(String playerName, List<String> tagStrings) {
        String tagString = String.join(EnumChatFormatting.WHITE + ", ", tagStrings);
        String message = EnumChatFormatting.BLACK + "[" +
                        EnumChatFormatting.DARK_PURPLE + "UE" +
                        EnumChatFormatting.BLACK + "] " +
                        EnumChatFormatting.WHITE + playerName +
                        EnumChatFormatting.YELLOW + " - " + tagString;
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(message));
    }

    private static class CheckTagsCommand extends CommandBase {
        @Override
        public String getCommandName() {
            return "urchintags";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/urchintags <player>";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length != 1) {
                sender.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.RED + "Usage: " + getCommandUsage(sender)));
                return;
            }
            checkPlayerTagsAsync(args[0]);
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }

    private static class SetApiKeyCommand extends CommandBase {
        @Override
        public String getCommandName() {
            return "urchinapikey";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/urchinapikey <key>";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length != 1) {
                sender.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.RED + "Usage: " + getCommandUsage(sender)));
                return;
            }
            
            apiKey = args[0];
            config.get("API", "apiKey", "").set(apiKey);
            config.save();
            sender.addChatMessage(new ChatComponentText(
                EnumChatFormatting.GREEN + "[UrchinExtras] API key updated successfully!"));
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }
}
