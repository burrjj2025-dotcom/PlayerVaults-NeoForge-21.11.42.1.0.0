package me.jjonlinux.playervaults;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

// The value here should match the mod_id in gradle.properties
@Mod(PlayerVaults.MODID)
public class PlayerVaults {
    public static final String MODID = "player_vaults";
    public static final Logger LOGGER = LogUtils.getLogger();

    // This mod registers no blocks, items or menu types of its own, so players
    // without it installed are not blocked from joining. Everything it does
    // happens in VaultEvents (commands, right-click item, chat prompt, saving).
    public PlayerVaults(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
