package me.jjonlinux.playervaults;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

// One player's vault file: config/player_vaults/<uuid>.json
//
// {
//   "player": "Name",
//   "uuid": "...",
//   "vaults": {
//     "1":    [ { "slot": 5, "item": { ...item data... } }, ... ],
//     "9000": [ ... ]
//   }
// }
//
// There is one entry under "vaults" per vault the player has opened, nothing more.
//
// Loading is forgiving: anything that doesn't make sense is skipped, and the next
// save writes the file back out with only the good parts (a bad item becomes an
// empty slot). Only a file with a real JSON syntax error is replaced as a whole.
public final class PlayerVaultData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public final UUID owner;
    public String name;
    // vault number -> list of { slot, item } entries (kept as raw JSON until the vault is opened)
    public final TreeMap<Integer, JsonArray> vaults = new TreeMap<>();

    public PlayerVaultData(UUID owner, String name) {
        this.owner = owner;
        this.name = name == null ? "unknown" : name;
    }

    private static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(PlayerVaults.MODID);
    }

    private static Path fileFor(UUID owner) {
        return folder().resolve(owner + ".json");
    }

    // Loads the player's file, or returns empty data if they have none yet.
    public static PlayerVaultData load(UUID owner, String fallbackName) {
        PlayerVaultData data = new PlayerVaultData(owner, fallbackName);
        Path file = fileFor(owner);
        if (!Files.exists(file)) {
            return data;
        }

        JsonElement parsed;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            parsed = JsonParser.parseReader(reader);
        } catch (IOException ex) {
            // Could be a temporary problem (permissions, disk). Refuse to open rather
            // than risk replacing a good file.
            throw new UncheckedIOException("Could not read " + file, ex);
        } catch (JsonParseException ex) {
            // The file isn't valid JSON at all (for example a missing comma), so nothing
            // in it can be read. It will be replaced the next time this vault is saved.
            PlayerVaults.LOGGER.error("{} is not valid JSON ({}). Its vaults will start empty "
                    + "and the file is replaced on the next save.", file, ex.getMessage());
            keepCopyOfBrokenFile(file);
            return data;
        }

        if (!parsed.isJsonObject()) {
            return data; // empty file, or something that isn't a vault file
        }
        JsonObject root = parsed.getAsJsonObject();

        JsonElement savedName = root.get("player");
        if (savedName != null && savedName.isJsonPrimitive()) {
            data.name = savedName.getAsString();
        }

        JsonElement vaultsElement = root.get("vaults");
        if (vaultsElement != null && vaultsElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : vaultsElement.getAsJsonObject().entrySet()) {
                try {
                    int number = Integer.parseInt(entry.getKey());
                    if (number < 1) {
                        continue;
                    }
                    JsonElement value = entry.getValue();
                    data.vaults.put(number, value.isJsonArray() ? value.getAsJsonArray() : new JsonArray());
                } catch (NumberFormatException ex) {
                    PlayerVaults.LOGGER.warn("{}: \"{}\" is not a vault number, ignoring that section.",
                            file, entry.getKey());
                }
            }
        }
        return data;
    }

    // Keeps a single copy (overwritten each time) of a file we could not read at all.
    // This does not change how vaults behave; it just lets you look at what was in it.
    private static void keepCopyOfBrokenFile(Path file) {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            PlayerVaults.LOGGER.warn("Could not keep a copy of {}", file, ex);
        }
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("player", this.name);
        root.addProperty("uuid", this.owner.toString());

        JsonObject vaultsJson = new JsonObject();
        for (Map.Entry<Integer, JsonArray> entry : this.vaults.entrySet()) {
            vaultsJson.add(Integer.toString(entry.getKey()), entry.getValue());
        }
        root.add("vaults", vaultsJson);

        Files.createDirectories(folder());
        Path file = fileFor(this.owner);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
