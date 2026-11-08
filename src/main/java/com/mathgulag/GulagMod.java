package com.mathgulag;

import com.mathgulag.game.ServerEvents;
import com.mathgulag.net.Network;
import net.fabricmc.api.ModInitializer;

public class GulagMod implements ModInitializer {
    public static final String MODID = "mathgulag";

    @Override
    public void onInitialize() {
        ModContent.register();
        Network.registerCommon();
        ServerEvents.register();
    }
}
