package com.famiglia.mod.data;

import com.famiglia.mod.FamigliaMod;
import com.famiglia.mod.gui.FamigliaScreen;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/**
 * Gestione dati della famiglia - completamente client-side.
 * Salva e carica da config/famiglia/famiglia_data.json
 */
public class FamigliaData {

    private static FamigliaData instance;

    // -- Family state --------------------------------------------------------
    private boolean inFamily = false;
    private String familyId = "";
    private String familyName = "La Famiglia";
    private String inviteCode = "";

    private List<ClientMember> membri = new ArrayList<>();
    private List<RuoloCustom> ruoli = new ArrayList<>();

    // -- Error state ---------------------------------------------------------
    private String lastError = "";
    private long lastErrorTime = 0;

    // -- Local persistence ---------------------------------------------------
    private final Path configDir;
    private final Path savePath;

    private FamigliaData() {
        configDir = FabricLoader.getInstance().getConfigDir().resolve("famiglia");
        savePath  = configDir.resolve("famiglia_data.json");
        try { Files.createDirectories(configDir); } catch (Exception ignored) {}
    }

    public static synchronized FamigliaData getInstance() {
        if (instance == null) instance = new FamigliaData();
        return instance;
    }

    // -- Getters -------------------------------------------------------------

    public boolean isInFamily()              { return inFamily; }
    public String getFamilyId()              { return familyId; }
    public String getNomeFamiglia()          { return familyName; }
    public String getInviteCode()            { return inviteCode; }
    public List<ClientMember> getMembri()    { return membri; }
    public List<RuoloCustom> getRuoli()      { return ruoli; }

    /** In local mode il creatore e' sempre il giocatore locale. */
    public boolean isCreator() {
        return inFamily;
    }

    public String getLastError() {
        if (System.currentTimeMillis() - lastErrorTime > 5000) lastError = "";
        return lastError;
    }

    public void setError(String msg) {
        lastError = msg;
        lastErrorTime = System.currentTimeMillis();
    }

    // -- Init ----------------------------------------------------------------

    /** Carica i dati locali all'apertura della GUI. */
    public void initLocal() {
        caricaLocale();
    }

    /** Reset dati (es. quando si cambia server). */
    public void reset() {
        // In local mode non facciamo reset perche' i dati sono persistenti
        // Ricarichiamo dal file
        caricaLocale();
    }

    // ========================================================================
    // AZIONI LOCALI
    // ========================================================================

    /** Crea una nuova famiglia. */
    public void createFamily(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = client.player != null
                ? client.player.getName().getString() : "Boss";

        inFamily = true;
        familyId = UUID.randomUUID().toString();
        familyName = name;
        inviteCode = generateInviteCode();

        ruoli = RuoloCustom.defaults();

        membri.clear();
        ClientMember me = new ClientMember();
        me.playerName = playerName;
        me.ruoloIndex = 0; // Boss
        me.stato = Membro.Stato.ONLINE;
        me.nota = "";
        me.dataIngresso = System.currentTimeMillis();
        membri.add(me);

        salvaLocale();
        refreshScreen();
    }

    /** Unisciti a una famiglia con codice invito (locale - per RP). */
    public void joinFamily(String code) {
        // In local mode, "unirsi" significa creare una famiglia con quel codice
        // come se fosse gia' stata condivisa
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = client.player != null
                ? client.player.getName().getString() : "Membro";

        inFamily = true;
        familyId = UUID.randomUUID().toString();
        familyName = "Famiglia (" + code + ")";
        inviteCode = code;

        ruoli = RuoloCustom.defaults();

        membri.clear();
        ClientMember me = new ClientMember();
        me.playerName = playerName;
        me.ruoloIndex = 0;
        me.stato = Membro.Stato.ONLINE;
        me.nota = "";
        me.dataIngresso = System.currentTimeMillis();
        membri.add(me);

        salvaLocale();
        refreshScreen();
    }

    /** Aggiungi un membro manualmente. */
    public void addMember(String nome, int ruoloIndex) {
        if (!inFamily) return;
        ClientMember m = new ClientMember();
        m.playerName = nome;
        m.ruoloIndex = Math.min(ruoloIndex, Math.max(0, ruoli.size() - 1));
        m.stato = Membro.Stato.OFFLINE;
        m.nota = "";
        m.dataIngresso = System.currentTimeMillis();
        membri.add(m);
        salvaLocale();
        refreshScreen();
    }

    /** Rimuovi un membro. */
    public void kickMember(int index) {
        if (index < 0 || index >= membri.size()) return;
        membri.remove(index);
        salvaLocale();
        refreshScreen();
    }

    /** Aggiorna il ruolo di un membro. */
    public void updateMemberRole(int memberIndex, int ruoloIndex) {
        if (memberIndex < 0 || memberIndex >= membri.size()) return;
        membri.get(memberIndex).ruoloIndex = Math.min(ruoloIndex, Math.max(0, ruoli.size() - 1));
        salvaLocale();
    }

    /** Aggiorna lo stato di un membro. */
    public void updateMemberStato(int memberIndex, Membro.Stato stato) {
        if (memberIndex < 0 || memberIndex >= membri.size()) return;
        membri.get(memberIndex).stato = stato;
        salvaLocale();
    }

    /** Aggiorna la nota di un membro. */
    public void updateMemberNota(int memberIndex, String nota) {
        if (memberIndex < 0 || memberIndex >= membri.size()) return;
        membri.get(memberIndex).nota = nota;
        salvaLocale();
    }

    /** Aggiungi un ruolo. */
    public void addRuolo(String nome, String emoji, int colore) {
        ruoli.add(new RuoloCustom(nome, emoji, colore));
        salvaLocale();
        refreshScreen();
    }

    /** Modifica un ruolo. */
    public void editRuolo(int index, String nome, String emoji, int colore) {
        if (index < 0 || index >= ruoli.size()) return;
        RuoloCustom r = ruoli.get(index);
        r.setNome(nome);
        r.setEmoji(emoji);
        r.setColore(colore);
        salvaLocale();
        refreshScreen();
    }

    /** Rimuovi un ruolo (non permette di cancellare l'ultimo). */
    public boolean removeRuolo(int index) {
        if (ruoli.size() <= 1) return false;
        if (index < 0 || index >= ruoli.size()) return false;
        for (ClientMember m : membri) {
            if (m.ruoloIndex == index) {
                m.ruoloIndex = 0;
            } else if (m.ruoloIndex > index) {
                m.ruoloIndex--;
            }
        }
        ruoli.remove(index);
        salvaLocale();
        refreshScreen();
        return true;
    }

    /** Aggiorna il nome della famiglia. */
    public void updateFamilyName(String name) {
        familyName = name;
        salvaLocale();
    }

    /** Rigenera il codice invito. */
    public void regenInviteCode() {
        inviteCode = generateInviteCode();
        salvaLocale();
        refreshScreen();
    }

    /** Lascia la famiglia (cancella tutto). */
    public void leaveFamily() {
        inFamily = false;
        familyId = "";
        familyName = "La Famiglia";
        inviteCode = "";
        membri.clear();
        ruoli.clear();
        try { Files.deleteIfExists(savePath); } catch (Exception ignored) {}
        refreshScreen();
    }

    // ========================================================================
    // PERSISTENZA LOCALE
    // ========================================================================

    public void salvaLocale() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("familyId", familyId);
            root.addProperty("familyName", familyName);
            root.addProperty("inviteCode", inviteCode);

            JsonArray ra = new JsonArray();
            for (RuoloCustom r : ruoli) {
                JsonObject ro = new JsonObject();
                ro.addProperty("nome", r.getNome());
                ro.addProperty("emoji", r.getEmoji());
                ro.addProperty("colore", r.getColore());
                ra.add(ro);
            }
            root.add("ruoli", ra);

            JsonArray ma = new JsonArray();
            for (ClientMember m : membri) {
                JsonObject mo = new JsonObject();
                mo.addProperty("playerName", m.playerName);
                mo.addProperty("ruoloIndex", m.ruoloIndex);
                mo.addProperty("stato", m.stato.name());
                mo.addProperty("nota", m.nota);
                mo.addProperty("dataIngresso", m.dataIngresso);
                ma.add(mo);
            }
            root.add("membri", ma);

            Files.createDirectories(configDir);
            Path tmpPath = configDir.resolve("famiglia_data.json.tmp");
            try (Writer w = new FileWriter(tmpPath.toFile())) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
            Files.move(tmpPath, savePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore salvataggio locale famiglia", e);
        }
    }

    public void caricaLocale() {
        if (!Files.exists(savePath)) return;
        try (Reader r = new FileReader(savePath.toFile())) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            familyId = root.has("familyId") ? root.get("familyId").getAsString() : "";
            familyName = root.has("familyName") ? root.get("familyName").getAsString() : "La Famiglia";
            inviteCode = root.has("inviteCode") ? root.get("inviteCode").getAsString() : "";

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
                        FamigliaMod.LOGGER.warn("Ruolo corrotto locale, ignorato", e);
                    }
                }
            }
            if (ruoli.isEmpty()) ruoli = RuoloCustom.defaults();

            membri.clear();
            if (root.has("membri")) {
                for (JsonElement el : root.getAsJsonArray("membri")) {
                    try {
                        JsonObject mo = el.getAsJsonObject();
                        ClientMember m = new ClientMember();
                        m.playerName = mo.get("playerName").getAsString();
                        m.ruoloIndex = mo.get("ruoloIndex").getAsInt();
                        m.stato = Membro.Stato.valueOf(mo.get("stato").getAsString());
                        m.nota = mo.has("nota") ? mo.get("nota").getAsString() : "";
                        m.dataIngresso = mo.has("dataIngresso") ? mo.get("dataIngresso").getAsLong() : 0;
                        m.ruoloIndex = Math.min(m.ruoloIndex, Math.max(0, ruoli.size() - 1));
                        membri.add(m);
                    } catch (Exception e) {
                        FamigliaMod.LOGGER.warn("Membro corrotto locale, ignorato", e);
                    }
                }
            }

            if (!familyId.isEmpty()) {
                inFamily = true;
            }
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore caricamento locale famiglia", e);
        }
    }

    private void refreshScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof FamigliaScreen screen) {
            screen.onDataUpdated();
        }
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    // -- Client member data class --------------------------------------------

    public static class ClientMember {
        public String playerName;
        public int ruoloIndex;
        public Membro.Stato stato;
        public String nota;
        public long dataIngresso;
    }
}
