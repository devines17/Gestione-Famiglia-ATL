package com.famiglia.mod.data;

import com.famiglia.mod.FamigliaMod;
import com.famiglia.mod.gui.FamigliaScreen;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;

import java.util.*;

/**
 * Cache lato client dei dati della famiglia.
 * Riceve gli aggiornamenti dal server tramite pacchetti S2C.
 */
public class FamigliaData {

    private static FamigliaData instance;

    // ── Family state ──────────────────────────────────────────────────────────
    private boolean inFamily = false;
    private String familyId = "";
    private String familyName = "La Famiglia";
    private String inviteCode = "";
    private UUID creatorUuid = null;

    private List<ClientMember> membri = new ArrayList<>();
    private List<RuoloCustom> ruoli = new ArrayList<>();

    // ── Error / notification state ────────────────────────────────────────────
    private String lastError = "";
    private long lastErrorTime = 0;

    private FamigliaData() {}

    public static synchronized FamigliaData getInstance() {
        if (instance == null) instance = new FamigliaData();
        return instance;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isInFamily()              { return inFamily; }
    public String getFamilyId()              { return familyId; }
    public String getNomeFamiglia()          { return familyName; }
    public String getInviteCode()            { return inviteCode; }
    public UUID getCreatorUuid()             { return creatorUuid; }
    public List<ClientMember> getMembri()    { return membri; }
    public List<RuoloCustom> getRuoli()      { return ruoli; }

    public boolean isCreator() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || creatorUuid == null) return false;
        return client.player.getUuid().equals(creatorUuid);
    }

    public String getLastError() {
        if (System.currentTimeMillis() - lastErrorTime > 5000) lastError = "";
        return lastError;
    }

    // ── Reset (on server join / disconnect) ───────────────────────────────────

    public void reset() {
        inFamily = false;
        familyId = "";
        familyName = "La Famiglia";
        inviteCode = "";
        creatorUuid = null;
        membri.clear();
        ruoli.clear();
        lastError = "";
    }

    // ── Handle S2C packets ────────────────────────────────────────────────────

    public void handleS2C(String action, String jsonData) {
        try {
            switch (action) {
                case "full_sync" -> handleFullSync(jsonData);
                case "no_family" -> {
                    inFamily = false;
                    familyId = "";
                    familyName = "La Famiglia";
                    inviteCode = "";
                    creatorUuid = null;
                    membri.clear();
                    ruoli.clear();
                    refreshScreen();
                }
                case "invite_code" -> {
                    JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
                    inviteCode = json.get("code").getAsString();
                    refreshScreen();
                }
                case "error" -> {
                    JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
                    lastError = json.get("message").getAsString();
                    lastErrorTime = System.currentTimeMillis();
                    refreshScreen();
                }
                default -> FamigliaMod.LOGGER.warn("Azione S2C sconosciuta: {}", action);
            }
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore gestione S2C: {} - {}", action, e.getMessage());
        }
    }

    private void handleFullSync(String jsonData) {
        try {
            JsonObject root = JsonParser.parseString(jsonData).getAsJsonObject();

            inFamily = true;
            familyId = root.get("id").getAsString();
            familyName = root.get("name").getAsString();
            inviteCode = root.has("inviteCode") ? root.get("inviteCode").getAsString() : "";
            creatorUuid = UUID.fromString(root.get("creatorUuid").getAsString());

            // Parse ruoli
            ruoli.clear();
            if (root.has("ruoli")) {
                for (JsonElement el : root.getAsJsonArray("ruoli")) {
                    try {
                        JsonObject ro = el.getAsJsonObject();
                        ruoli.add(new RuoloCustom(
                                ro.get("nome").getAsString(),
                                ro.get("emoji").getAsString(),
                                ro.get("colore").getAsInt()));
                    } catch (Exception e) {
                        FamigliaMod.LOGGER.warn("Ruolo corrotto nel sync, ignorato", e);
                    }
                }
            }
            if (ruoli.isEmpty()) ruoli = RuoloCustom.defaults();

            // Parse membri
            membri.clear();
            if (root.has("membri")) {
                for (JsonElement el : root.getAsJsonArray("membri")) {
                    try {
                        JsonObject mo = el.getAsJsonObject();
                        ClientMember m = new ClientMember();
                        m.playerUuid = UUID.fromString(mo.get("uuid").getAsString());
                        m.playerName = mo.get("playerName").getAsString();
                        m.ruoloIndex = mo.get("ruoloIndex").getAsInt();
                        m.stato = Membro.Stato.valueOf(mo.get("stato").getAsString());
                        m.nota = mo.has("nota") ? mo.get("nota").getAsString() : "";
                        m.dataIngresso = mo.has("dataIngresso") ? mo.get("dataIngresso").getAsLong() : 0;
                        // Clamp ruoloIndex
                        m.ruoloIndex = Math.min(m.ruoloIndex, Math.max(0, ruoli.size() - 1));
                        membri.add(m);
                    } catch (Exception e) {
                        FamigliaMod.LOGGER.warn("Membro corrotto nel sync, ignorato", e);
                    }
                }
            }

            refreshScreen();
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore parsing full_sync", e);
        }
    }

    private void refreshScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof FamigliaScreen screen) {
            screen.onDataUpdated();
        }
    }

    // ── Client member data class ──────────────────────────────────────────────

    public static class ClientMember {
        public UUID playerUuid;
        public String playerName;
        public int ruoloIndex;
        public Membro.Stato stato;
        public String nota;
        public long dataIngresso;
    }
}
