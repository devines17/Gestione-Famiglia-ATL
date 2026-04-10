package com.famiglia.mod.client;

import com.famiglia.mod.FamigliaMod;
import com.famiglia.mod.data.FamigliaData;
import com.famiglia.mod.gui.FamigliaScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class FamigliaClientMod implements ClientModInitializer {

    public static KeyBinding openFamigliaKey;

    @Override
    public void onInitializeClient() {

        // -- Registrazione keybind -------------------------------------------
        openFamigliaKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.famiglia.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.famiglia"
        ));

        // -- Tick listener ---------------------------------------------------
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openFamigliaKey.wasPressed()) {
                if (client.player != null) {
                    if (client.currentScreen instanceof FamigliaScreen) {
                        client.setScreen(null);
                    } else {
                        // Carica dati locali e apri la GUI
                        FamigliaData.getInstance().initLocal();
                        client.setScreen(new FamigliaScreen());
                    }
                }
            }
        });

        FamigliaMod.LOGGER.info("[Famiglia] Client inizializzato. Keybind default: F6");
    }
}
