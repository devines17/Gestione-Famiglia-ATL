package com.famiglia.mod.client;

import com.famiglia.mod.FamigliaMod;
import com.famiglia.mod.data.FamigliaData;
import com.famiglia.mod.gui.FamigliaScreen;
import com.famiglia.mod.network.FamigliaC2SPayload;
import com.famiglia.mod.network.FamigliaS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class FamigliaClientMod implements ClientModInitializer {

    public static KeyBinding openFamigliaKey;

    @Override
    public void onInitializeClient() {

        // ── Registrazione keybind ────────────────────────────────────────────
        openFamigliaKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.famiglia.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.famiglia"
        ));

        // ── Tick listener ────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openFamigliaKey.wasPressed()) {
                if (client.player != null) {
                    if (client.currentScreen instanceof FamigliaScreen) {
                        client.setScreen(null);
                    } else {
                        // Richiedi sync al server quando si apre la GUI
                        if (ClientPlayNetworking.canSend(FamigliaC2SPayload.ID)) {
                            ClientPlayNetworking.send(new FamigliaC2SPayload("request_sync", "{}"));
                        }
                        client.setScreen(new FamigliaScreen());
                    }
                }
            }
        });

        // ── Ricezione pacchetti S2C ──────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(FamigliaS2CPayload.ID,
                (payload, context) -> {
                    String action = payload.action();
                    String jsonData = payload.jsonData();

                    context.client().execute(() ->
                            FamigliaData.getInstance().handleS2C(action, jsonData));
                });

        // ── Quando ci si connette a un server, richiedi sync ─────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            FamigliaData.getInstance().reset();
        });

        FamigliaMod.LOGGER.info("[Famiglia] Client inizializzato. Keybind default: F6");
    }
}
