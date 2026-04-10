package com.famiglia.mod;

import com.famiglia.mod.data.Membro;
import com.famiglia.mod.network.FamigliaC2SPayload;
import com.famiglia.mod.network.FamigliaS2CPayload;
import com.famiglia.mod.server.ServerFamigliaManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class FamigliaMod implements ModInitializer {
    public static final String MOD_ID = "famiglia";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Famiglia Mod caricata.");

        // ── Registra i tipi di pacchetto ──────────────────────────────────────
        PayloadTypeRegistry.playC2S().register(FamigliaC2SPayload.ID, FamigliaC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FamigliaS2CPayload.ID, FamigliaS2CPayload.CODEC);

        // ── Server lifecycle events ───────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                ServerFamigliaManager.getInstance().onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                ServerFamigliaManager.getInstance().onServerStopping(server));

        // ── Player join/disconnect ────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerFamigliaManager.getInstance().onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ServerFamigliaManager.getInstance().onPlayerDisconnect(handler.getPlayer()));

        // ── Gestione pacchetti C2S ────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(FamigliaC2SPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    String action = payload.action();
                    String jsonData = payload.jsonData();

                    context.player().getServer().execute(() ->
                            handleC2S(player, action, jsonData));
                });
    }

    private void handleC2S(ServerPlayerEntity player, String action, String jsonData) {
        ServerFamigliaManager mgr = ServerFamigliaManager.getInstance();
        UUID uuid = player.getUuid();
        String name = player.getName().getString();

        try {
            JsonObject json = jsonData.isEmpty() || jsonData.equals("{}")
                    ? new JsonObject()
                    : JsonParser.parseString(jsonData).getAsJsonObject();

            switch (action) {
                case "create_family" -> {
                    String familyName = json.has("name") ? json.get("name").getAsString() : "La Famiglia";
                    String familyId = mgr.createFamily(uuid, name, familyName);
                    if (familyId != null) {
                        mgr.sendInviteCode(player);
                        mgr.broadcastFamilySync(uuid);
                    } else {
                        mgr.sendError(player, "Sei gia in una famiglia!");
                    }
                }
                case "join_family" -> {
                    String code = json.has("code") ? json.get("code").getAsString() : "";
                    String familyId = mgr.joinFamily(uuid, name, code);
                    if (familyId != null) {
                        mgr.broadcastFamilySync(uuid);
                    } else {
                        mgr.sendError(player, "Codice non valido o sei gia in una famiglia.");
                    }
                }
                case "leave_family" -> {
                    if (mgr.leaveFamily(uuid)) {
                        mgr.sendSyncToPlayer(player);
                    } else {
                        mgr.sendError(player, "Non sei in nessuna famiglia.");
                    }
                }
                case "update_status" -> {
                    String statoStr = json.has("status") ? json.get("status").getAsString() : "ONLINE";
                    try {
                        Membro.Stato stato = Membro.Stato.valueOf(statoStr);
                        if (mgr.updateStatus(uuid, stato)) {
                            mgr.broadcastFamilySync(uuid);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
                case "update_nota" -> {
                    UUID targetUuid = json.has("targetUuid")
                            ? UUID.fromString(json.get("targetUuid").getAsString()) : uuid;
                    String nota = json.has("nota") ? json.get("nota").getAsString() : "";
                    if (mgr.updateNota(uuid, targetUuid, nota)) {
                        mgr.broadcastFamilySync(uuid);
                    }
                }
                case "update_member_role" -> {
                    UUID targetUuid = UUID.fromString(json.get("targetUuid").getAsString());
                    int ruoloIndex = json.get("ruoloIndex").getAsInt();
                    if (mgr.updateMemberRole(uuid, targetUuid, ruoloIndex)) {
                        mgr.broadcastFamilySync(uuid);
                    } else {
                        mgr.sendError(player, "Solo il Boss puo cambiare i ruoli.");
                    }
                }
                case "kick_member" -> {
                    UUID targetUuid = UUID.fromString(json.get("targetUuid").getAsString());
                    if (mgr.kickMember(uuid, targetUuid)) {
                        mgr.broadcastFamilySync(uuid);
                        ServerPlayerEntity kicked = player.getServer().getPlayerManager().getPlayer(targetUuid);
                        if (kicked != null) {
                            mgr.sendSyncToPlayer(kicked);
                        }
                    } else {
                        mgr.sendError(player, "Solo il Boss puo espellere membri.");
                    }
                }
                case "add_ruolo" -> {
                    String nome = json.get("nome").getAsString();
                    String emoji = json.has("emoji") ? json.get("emoji").getAsString() : "*";
                    int colore = json.has("colore") ? json.get("colore").getAsInt() : 0xFFAAAAAA;
                    if (mgr.addRuolo(uuid, nome, emoji, colore)) {
                        mgr.broadcastFamilySync(uuid);
                    } else {
                        mgr.sendError(player, "Solo il Boss puo aggiungere ruoli.");
                    }
                }
                case "edit_ruolo" -> {
                    int index = json.get("index").getAsInt();
                    String nome = json.get("nome").getAsString();
                    String emoji = json.has("emoji") ? json.get("emoji").getAsString() : "*";
                    int colore = json.has("colore") ? json.get("colore").getAsInt() : 0xFFAAAAAA;
                    if (mgr.editRuolo(uuid, index, nome, emoji, colore)) {
                        mgr.broadcastFamilySync(uuid);
                    } else {
                        mgr.sendError(player, "Solo il Boss puo modificare ruoli.");
                    }
                }
                case "remove_ruolo" -> {
                    int index = json.get("index").getAsInt();
                    if (mgr.removeRuolo(uuid, index)) {
                        mgr.broadcastFamilySync(uuid);
                    } else {
                        mgr.sendError(player, "Non puoi eliminare l'ultimo ruolo.");
                    }
                }
                case "update_family_name" -> {
                    String newName = json.has("name") ? json.get("name").getAsString() : "La Famiglia";
                    if (mgr.updateFamilyName(uuid, newName)) {
                        mgr.broadcastFamilySync(uuid);
                    } else {
                        mgr.sendError(player, "Solo il Boss puo cambiare il nome.");
                    }
                }
                case "regen_invite_code" -> {
                    if (mgr.regenerateInviteCode(uuid)) {
                        mgr.sendInviteCode(player);
                        mgr.broadcastFamilySync(uuid);
                    } else {
                        mgr.sendError(player, "Solo il Boss puo rigenerare il codice.");
                    }
                }
                case "request_sync" -> mgr.sendSyncToPlayer(player);
                case "get_invite_code" -> mgr.sendInviteCode(player);
                default -> LOGGER.warn("Azione C2S sconosciuta: {}", action);
            }
        } catch (Exception e) {
            LOGGER.error("Errore gestione pacchetto C2S: {} - {}", action, e.getMessage());
            mgr.sendError(player, "Errore interno.");
        }
    }
}
