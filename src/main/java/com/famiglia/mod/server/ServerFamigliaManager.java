package com.famiglia.mod.server;

import com.famiglia.mod.FamigliaMod;
import com.famiglia.mod.data.Membro;
import com.famiglia.mod.data.RuoloCustom;
import com.famiglia.mod.network.FamigliaS2CPayload;
import com.google.gson.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestisce tutte le famiglie lato server.
 * Ogni mondo ha le proprie famiglie, salvate in JSON nella cartella del mondo.
 */
public class ServerFamigliaManager {

    private static ServerFamigliaManager instance;

    /** familyId → ServerFamily */
    private final Map<String, ServerFamily> families = new ConcurrentHashMap<>();
    /** playerUUID → familyId */
    private final Map<UUID, String> playerFamilyMap = new ConcurrentHashMap<>();
    /** inviteCode → familyId */
    private final Map<String, String> inviteCodeMap = new ConcurrentHashMap<>();

    private MinecraftServer server;
    private Path savePath;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public static ServerFamigliaManager getInstance() {
        if (instance == null) instance = new ServerFamigliaManager();
        return instance;
    }

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        this.savePath = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("famiglia_data.json");
        carica();
        FamigliaMod.LOGGER.info("[Famiglia] Dati famiglie caricati: {} famiglie", families.size());
    }

    public void onServerStopping(MinecraftServer server) {
        salva();
        FamigliaMod.LOGGER.info("[Famiglia] Dati famiglie salvati.");
    }

    // ── Family CRUD ───────────────────────────────────────────────────────────

    /** Crea una nuova famiglia. Restituisce l'ID della famiglia creata. */
    public String createFamily(UUID creatorUuid, String creatorName, String familyName) {
        // Se il giocatore è già in una famiglia, non può crearne un'altra
        if (playerFamilyMap.containsKey(creatorUuid)) return null;

        String familyId = UUID.randomUUID().toString();
        String inviteCode = generateInviteCode();

        ServerFamily family = new ServerFamily();
        family.id = familyId;
        family.name = familyName;
        family.inviteCode = inviteCode;
        family.creatorUuid = creatorUuid;
        family.roles = RuoloCustom.defaults();

        ServerMember creator = new ServerMember();
        creator.playerUuid = creatorUuid;
        creator.playerName = creatorName;
        creator.ruoloIndex = 0; // Boss
        creator.stato = Membro.Stato.ONLINE;
        creator.nota = "";
        creator.dataIngresso = System.currentTimeMillis();
        family.members.add(creator);

        families.put(familyId, family);
        playerFamilyMap.put(creatorUuid, familyId);
        inviteCodeMap.put(inviteCode, familyId);

        salva();
        return familyId;
    }

    /** Unisce un giocatore a una famiglia tramite codice invito. Restituisce il familyId o null. */
    public String joinFamily(UUID playerUuid, String playerName, String code) {
        if (playerFamilyMap.containsKey(playerUuid)) return null; // già in una famiglia

        String familyId = inviteCodeMap.get(code.toUpperCase());
        if (familyId == null) return null;

        ServerFamily family = families.get(familyId);
        if (family == null) return null;

        // Controlla se il giocatore è già membro (rientro)
        for (ServerMember m : family.members) {
            if (m.playerUuid.equals(playerUuid)) {
                playerFamilyMap.put(playerUuid, familyId);
                m.stato = Membro.Stato.ONLINE;
                salva();
                return familyId;
            }
        }

        ServerMember member = new ServerMember();
        member.playerUuid = playerUuid;
        member.playerName = playerName;
        member.ruoloIndex = Math.min(family.roles.size() - 1, family.roles.size() - 1); // Ultimo ruolo
        member.stato = Membro.Stato.ONLINE;
        member.nota = "";
        member.dataIngresso = System.currentTimeMillis();
        family.members.add(member);

        playerFamilyMap.put(playerUuid, familyId);

        salva();
        return familyId;
    }

    /** Rimuove un giocatore dalla famiglia. */
    public boolean leaveFamily(UUID playerUuid) {
        String familyId = playerFamilyMap.get(playerUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) { playerFamilyMap.remove(playerUuid); return false; }

        family.members.removeIf(m -> m.playerUuid.equals(playerUuid));
        playerFamilyMap.remove(playerUuid);

        // Se la famiglia è vuota, eliminala
        if (family.members.isEmpty()) {
            families.remove(familyId);
            inviteCodeMap.remove(family.inviteCode);
        }

        salva();
        return true;
    }

    /** Rimuove un membro dalla famiglia (kick). Solo il creatore può farlo. */
    public boolean kickMember(UUID adminUuid, UUID targetUuid) {
        String familyId = playerFamilyMap.get(adminUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;
        if (!family.creatorUuid.equals(adminUuid)) return false; // Solo il boss può kickare
        if (adminUuid.equals(targetUuid)) return false; // Non puoi kickarti

        family.members.removeIf(m -> m.playerUuid.equals(targetUuid));
        playerFamilyMap.remove(targetUuid);

        salva();
        return true;
    }

    // ── Status & Member Updates ───────────────────────────────────────────────

    public boolean updateStatus(UUID playerUuid, Membro.Stato stato) {
        String familyId = playerFamilyMap.get(playerUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;

        for (ServerMember m : family.members) {
            if (m.playerUuid.equals(playerUuid)) {
                m.stato = stato;
                salva();
                return true;
            }
        }
        return false;
    }

    public boolean updateNota(UUID playerUuid, UUID targetUuid, String nota) {
        String familyId = playerFamilyMap.get(playerUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;

        for (ServerMember m : family.members) {
            if (m.playerUuid.equals(targetUuid)) {
                m.nota = nota;
                salva();
                return true;
            }
        }
        return false;
    }

    public boolean updateMemberRole(UUID adminUuid, UUID targetUuid, int ruoloIndex) {
        String familyId = playerFamilyMap.get(adminUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;
        if (!family.creatorUuid.equals(adminUuid)) return false;
        if (ruoloIndex < 0 || ruoloIndex >= family.roles.size()) return false;

        for (ServerMember m : family.members) {
            if (m.playerUuid.equals(targetUuid)) {
                m.ruoloIndex = ruoloIndex;
                salva();
                return true;
            }
        }
        return false;
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    public boolean addRuolo(UUID adminUuid, String nome, String emoji, int colore) {
        String familyId = playerFamilyMap.get(adminUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;
        if (!family.creatorUuid.equals(adminUuid)) return false;

        family.roles.add(new RuoloCustom(nome, emoji, colore));
        salva();
        return true;
    }

    public boolean editRuolo(UUID adminUuid, int index, String nome, String emoji, int colore) {
        String familyId = playerFamilyMap.get(adminUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;
        if (!family.creatorUuid.equals(adminUuid)) return false;
        if (index < 0 || index >= family.roles.size()) return false;

        RuoloCustom r = family.roles.get(index);
        r.setNome(nome);
        r.setEmoji(emoji);
        r.setColore(colore);
        salva();
        return true;
    }

    public boolean removeRuolo(UUID adminUuid, int index) {
        String familyId = playerFamilyMap.get(adminUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;
        if (!family.creatorUuid.equals(adminUuid)) return false;
        if (family.roles.size() <= 1) return false;
        if (index < 0 || index >= family.roles.size()) return false;

        family.roles.remove(index);
        // Aggiorna i ruoloIndex dei membri che avevano quel ruolo
        for (ServerMember m : family.members) {
            if (m.ruoloIndex == index) {
                m.ruoloIndex = 0;
            } else if (m.ruoloIndex > index) {
                m.ruoloIndex--;
            }
        }
        salva();
        return true;
    }

    // ── Family Profile ────────────────────────────────────────────────────────

    public boolean updateFamilyName(UUID adminUuid, String name) {
        String familyId = playerFamilyMap.get(adminUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;
        if (!family.creatorUuid.equals(adminUuid)) return false;

        family.name = name;
        salva();
        return true;
    }

    public boolean regenerateInviteCode(UUID adminUuid) {
        String familyId = playerFamilyMap.get(adminUuid);
        if (familyId == null) return false;

        ServerFamily family = families.get(familyId);
        if (family == null) return false;
        if (!family.creatorUuid.equals(adminUuid)) return false;

        inviteCodeMap.remove(family.inviteCode);
        family.inviteCode = generateInviteCode();
        inviteCodeMap.put(family.inviteCode, familyId);
        salva();
        return true;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public ServerFamily getFamilyForPlayer(UUID playerUuid) {
        String familyId = playerFamilyMap.get(playerUuid);
        if (familyId == null) return null;
        return families.get(familyId);
    }

    public boolean isCreator(UUID playerUuid) {
        ServerFamily family = getFamilyForPlayer(playerUuid);
        return family != null && family.creatorUuid.equals(playerUuid);
    }

    public String getInviteCode(UUID playerUuid) {
        ServerFamily family = getFamilyForPlayer(playerUuid);
        return family != null ? family.inviteCode : null;
    }

    // ── Broadcasting ──────────────────────────────────────────────────────────

    /** Invia un sync completo a tutti i membri online della famiglia. */
    public void broadcastFamilySync(UUID triggerPlayerUuid) {
        ServerFamily family = getFamilyForPlayer(triggerPlayerUuid);
        if (family == null || server == null) return;

        String syncJson = familyToJson(family);
        FamigliaS2CPayload payload = new FamigliaS2CPayload("full_sync", syncJson);

        for (ServerMember m : family.members) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(m.playerUuid);
            if (player != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    /** Invia un sync solo a un giocatore specifico. */
    public void sendSyncToPlayer(ServerPlayerEntity player) {
        ServerFamily family = getFamilyForPlayer(player.getUuid());
        if (family == null) {
            // Il giocatore non è in nessuna famiglia
            ServerPlayNetworking.send(player,
                    new FamigliaS2CPayload("no_family", "{}"));
            return;
        }

        String syncJson = familyToJson(family);
        ServerPlayNetworking.send(player,
                new FamigliaS2CPayload("full_sync", syncJson));
    }

    /** Invia un errore al giocatore. */
    public void sendError(ServerPlayerEntity player, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("message", message);
        ServerPlayNetworking.send(player,
                new FamigliaS2CPayload("error", json.toString()));
    }

    /** Invia il codice invito al giocatore. */
    public void sendInviteCode(ServerPlayerEntity player) {
        String code = getInviteCode(player.getUuid());
        if (code == null) return;
        JsonObject json = new JsonObject();
        json.addProperty("code", code);
        ServerPlayNetworking.send(player,
                new FamigliaS2CPayload("invite_code", json.toString()));
    }

    // ── Player Connect/Disconnect ─────────────────────────────────────────────

    public void onPlayerJoin(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        ServerFamily family = getFamilyForPlayer(uuid);
        if (family != null) {
            // Aggiorna il nome del giocatore (potrebbe essere cambiato)
            for (ServerMember m : family.members) {
                if (m.playerUuid.equals(uuid)) {
                    m.playerName = player.getName().getString();
                    m.stato = Membro.Stato.ONLINE;
                    break;
                }
            }
            salva();
            broadcastFamilySync(uuid);
        }
    }

    public void onPlayerDisconnect(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        ServerFamily family = getFamilyForPlayer(uuid);
        if (family != null) {
            for (ServerMember m : family.members) {
                if (m.playerUuid.equals(uuid)) {
                    m.stato = Membro.Stato.OFFLINE;
                    break;
                }
            }
            salva();
            // Broadcast ai rimanenti online
            broadcastFamilySync(uuid);
        }
    }

    // ── JSON Serialization ────────────────────────────────────────────────────

    private String familyToJson(ServerFamily family) {
        JsonObject root = new JsonObject();
        root.addProperty("id", family.id);
        root.addProperty("name", family.name);
        root.addProperty("inviteCode", family.inviteCode);
        root.addProperty("creatorUuid", family.creatorUuid.toString());

        JsonArray rolesArr = new JsonArray();
        for (RuoloCustom r : family.roles) {
            JsonObject ro = new JsonObject();
            ro.addProperty("nome", r.getNome());
            ro.addProperty("emoji", r.getEmoji());
            ro.addProperty("colore", r.getColore());
            rolesArr.add(ro);
        }
        root.add("ruoli", rolesArr);

        JsonArray membersArr = new JsonArray();
        for (ServerMember m : family.members) {
            JsonObject mo = new JsonObject();
            mo.addProperty("uuid", m.playerUuid.toString());
            mo.addProperty("playerName", m.playerName);
            mo.addProperty("ruoloIndex", m.ruoloIndex);
            mo.addProperty("stato", m.stato.name());
            mo.addProperty("nota", m.nota);
            mo.addProperty("dataIngresso", m.dataIngresso);
            membersArr.add(mo);
        }
        root.add("membri", membersArr);

        return root.toString();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void salva() {
        if (savePath == null) return;
        try {
            JsonObject root = new JsonObject();
            JsonArray familiesArr = new JsonArray();

            for (ServerFamily family : families.values()) {
                JsonObject fo = new JsonObject();
                fo.addProperty("id", family.id);
                fo.addProperty("name", family.name);
                fo.addProperty("inviteCode", family.inviteCode);
                fo.addProperty("creatorUuid", family.creatorUuid.toString());

                JsonArray rolesArr = new JsonArray();
                for (RuoloCustom r : family.roles) {
                    JsonObject ro = new JsonObject();
                    ro.addProperty("nome", r.getNome());
                    ro.addProperty("emoji", r.getEmoji());
                    ro.addProperty("colore", r.getColore());
                    rolesArr.add(ro);
                }
                fo.add("ruoli", rolesArr);

                JsonArray membersArr = new JsonArray();
                for (ServerMember m : family.members) {
                    JsonObject mo = new JsonObject();
                    mo.addProperty("uuid", m.playerUuid.toString());
                    mo.addProperty("playerName", m.playerName);
                    mo.addProperty("ruoloIndex", m.ruoloIndex);
                    mo.addProperty("stato", m.stato.name());
                    mo.addProperty("nota", m.nota);
                    mo.addProperty("dataIngresso", m.dataIngresso);
                    membersArr.add(mo);
                }
                fo.add("membri", membersArr);
                familiesArr.add(fo);
            }
            root.add("families", familiesArr);

            Path tmpPath = savePath.resolveSibling("famiglia_data.json.tmp");
            Files.createDirectories(savePath.getParent());
            try (Writer w = new FileWriter(tmpPath.toFile())) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
            Files.move(tmpPath, savePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore salvataggio dati famiglie server", e);
        }
    }

    private void carica() {
        families.clear();
        playerFamilyMap.clear();
        inviteCodeMap.clear();

        if (savePath == null || !Files.exists(savePath)) return;

        try (Reader r = new FileReader(savePath.toFile())) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (!root.has("families")) return;

            for (JsonElement el : root.getAsJsonArray("families")) {
                try {
                    JsonObject fo = el.getAsJsonObject();
                    ServerFamily family = new ServerFamily();
                    family.id = fo.get("id").getAsString();
                    family.name = fo.get("name").getAsString();
                    family.inviteCode = fo.get("inviteCode").getAsString();
                    family.creatorUuid = UUID.fromString(fo.get("creatorUuid").getAsString());

                    family.roles.clear();
                    if (fo.has("ruoli")) {
                        for (JsonElement re : fo.getAsJsonArray("ruoli")) {
                            try {
                                JsonObject ro = re.getAsJsonObject();
                                family.roles.add(new RuoloCustom(
                                        ro.get("nome").getAsString(),
                                        ro.get("emoji").getAsString(),
                                        ro.get("colore").getAsInt()));
                            } catch (Exception ex) {
                                FamigliaMod.LOGGER.warn("Ruolo corrotto, ignorato", ex);
                            }
                        }
                    }
                    if (family.roles.isEmpty()) family.roles = RuoloCustom.defaults();

                    if (fo.has("membri")) {
                        for (JsonElement me : fo.getAsJsonArray("membri")) {
                            try {
                                JsonObject mo = me.getAsJsonObject();
                                ServerMember m = new ServerMember();
                                m.playerUuid = UUID.fromString(mo.get("uuid").getAsString());
                                m.playerName = mo.get("playerName").getAsString();
                                m.ruoloIndex = mo.get("ruoloIndex").getAsInt();
                                m.stato = Membro.Stato.valueOf(mo.get("stato").getAsString());
                                m.nota = mo.has("nota") ? mo.get("nota").getAsString() : "";
                                m.dataIngresso = mo.has("dataIngresso") ? mo.get("dataIngresso").getAsLong() : System.currentTimeMillis();
                                // Clamp ruoloIndex
                                m.ruoloIndex = Math.min(m.ruoloIndex, Math.max(0, family.roles.size() - 1));
                                family.members.add(m);
                                playerFamilyMap.put(m.playerUuid, family.id);
                            } catch (Exception ex) {
                                FamigliaMod.LOGGER.warn("Membro corrotto, ignorato", ex);
                            }
                        }
                    }

                    families.put(family.id, family);
                    inviteCodeMap.put(family.inviteCode, family.id);
                } catch (Exception ex) {
                    FamigliaMod.LOGGER.warn("Famiglia corrotta, ignorata", ex);
                }
            }
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore caricamento dati famiglie server", e);
        }
    }

    // ── Invite Code Generator ─────────────────────────────────────────────────

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        String code = sb.toString();
        // Evita collisioni
        while (inviteCodeMap.containsKey(code)) {
            sb.setLength(0);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
            }
            code = sb.toString();
        }
        return code;
    }

    // ── Inner Data Classes ────────────────────────────────────────────────────

    public static class ServerFamily {
        public String id;
        public String name;
        public String inviteCode;
        public UUID creatorUuid;
        public List<RuoloCustom> roles = new ArrayList<>(RuoloCustom.defaults());
        public List<ServerMember> members = new ArrayList<>();
    }

    public static class ServerMember {
        public UUID playerUuid;
        public String playerName;
        public int ruoloIndex;
        public Membro.Stato stato;
        public String nota;
        public long dataIngresso;
    }
}
