package com.famiglia.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FamigliaMod implements ModInitializer {
    public static final String MOD_ID = "famiglia";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Famiglia Mod caricata.");
    }
}
