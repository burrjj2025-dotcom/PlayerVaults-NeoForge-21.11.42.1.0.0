package me.jjonlinux.playervaults;

import net.neoforged.neoforge.common.ModConfigSpec;

// Mod settings (not vault data). Shows up as config/player_vaults-common.toml
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_VAULT_NUMBER = BUILDER
            .comment("The highest vault number a player can open. Vaults only take up disk space once they are opened.")
            .defineInRange("maxVaultNumber", 1_000_000, 1, Integer.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();
}
