package com.famiglia.mod.client;

import com.famiglia.mod.FamigliaMod;
import com.famiglia.mod.gui.FamigliaScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class FamigliaClientMod implements ClientModInitializer {

    /**
     * Keybind esposta pubblicamente così la GUI può mostrarla nel tooltip.
     * Il giocatore può cambiarla da:
     *   Opzioni → Controlli → Famiglia Mod → Apri Pannello Famiglia
     */
    public static KeyBinding openFamigliaKey;

    @Override
    public void onInitializeClient() {

        // ── Registrazione keybind ────────────────────────────────────────────
        // "key.famiglia.open"       → chiave traduzione (it_it.json / en_us.json)
        // "category.famiglia"       → categoria mostrata nelle opzioni controlli
        // Default: F6, liberamente modificabile dal giocatore in-game
        openFamigliaKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.famiglia.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.famiglia"
        ));

        // ── Tick listener ────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // wasPressed() consuma tutti i press accumulati tra i tick
            while (openFamigliaKey.wasPressed()) {
                if (client.player != null) {
                    // Alterna: se la GUI è già aperta la chiude, altrimenti la apre
                    if (client.currentScreen instanceof FamigliaScreen) {
                        client.setScreen(null);
                    } else {
                        client.setScreen(new FamigliaScreen());
                    }
                }
            }
        });

        FamigliaMod.LOGGER.info("[Famiglia] Client inizializzato. Keybind default: F6");
    }
}
