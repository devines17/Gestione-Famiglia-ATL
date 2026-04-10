package com.famiglia.mod.data;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import com.famiglia.mod.FamigliaMod;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FamigliaData {

    private static FamigliaData instance;

    private List<Membro>      membri        = new ArrayList<>();
    private List<RuoloCustom> ruoli         = new ArrayList<>();
    private String            nomeFamiglia  = "La Famiglia";
    private String            fotoNomeFile  = "";

    private Identifier        fotoTextureId = null;
    private boolean           fotoCaricata  = false;

    private final Path configDir;
    private final Path savePath;

    private FamigliaData() {
        configDir = FabricLoader.getInstance().getConfigDir().resolve("famiglia");
        savePath  = configDir.resolve("famiglia_data.json");
        try { Files.createDirectories(configDir); } catch (Exception ignored) {}
        carica();
        if (ruoli.isEmpty())  ruoli  = RuoloCustom.defaults();
        if (membri.isEmpty()) caricaDatiDemo();
    }

    public static synchronized FamigliaData getInstance() {
        if (instance == null) instance = new FamigliaData();
        return instance;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public List<Membro>      getMembri()              { return membri; }
    public List<RuoloCustom> getRuoli()               { return ruoli; }
    public String            getNomeFamiglia()        { return nomeFamiglia; }
    public void              setNomeFamiglia(String n){ nomeFamiglia = n; salva(); }
    public String            getFotoNomeFile()        { return fotoNomeFile; }
    public Path              getConfigDir()           { return configDir; }

    public void setFotoNomeFile(String nome) {
        this.fotoNomeFile  = nome;
        this.fotoCaricata  = false;
        this.fotoTextureId = null;
        salva();
    }

    /** Carica e restituisce la texture foto; null se non disponibile. */
    public Identifier getFotoTexture() {
        if (fotoCaricata) return fotoTextureId;
        fotoCaricata = true;
        if (fotoNomeFile == null || fotoNomeFile.isBlank()) return null;
        Path imgPath = configDir.resolve(fotoNomeFile);
        if (!Files.exists(imgPath)) return null;
        try (InputStream is = Files.newInputStream(imgPath)) {
            NativeImage img = NativeImage.read(is);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = new Identifier("famiglia", "foto_famiglia");
            // Libera la texture precedente per evitare memory leak
            if (fotoTextureId != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(fotoTextureId);
            }
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            fotoTextureId = id;
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore caricamento foto famiglia", e);
            fotoTextureId = null;
        }
        return fotoTextureId;
    }

    /** Lista di file PNG/JPG presenti in config/famiglia/ */
    public List<String> getImmaginiDisponibili() {
        List<String> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(configDir, p -> {
            String n = p.getFileName().toString().toLowerCase();
            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
        })) { for (Path p : ds) list.add(p.getFileName().toString()); }
        catch (Exception ignored) {}
        return list;
    }

    // ── Membri ───────────────────────────────────────────────────────────────

    public void aggiungiMembro(Membro m) { membri.add(m); salva(); }
    public void rimuoviMembro(Membro m)  { membri.remove(m); salva(); }

    // ── Ruoli ────────────────────────────────────────────────────────────────

    public void aggiungiRuolo(RuoloCustom r) { ruoli.add(r); salva(); }
    /** Rimuove un ruolo. Non permette di cancellare l'ultimo ruolo rimasto. */
    public boolean rimuoviRuolo(RuoloCustom r) {
        if (ruoli.size() <= 1) return false;
        RuoloCustom fallback = ruoli.stream()
            .filter(x -> x != r)
            .findFirst()
            .orElse(ruoli.get(0));
        for (Membro m : membri) {
            if (m.getRuolo() == r) m.setRuolo(fallback);
        }
        ruoli.remove(r);
        salva();
        return true;
    }

    // ── JSON ─────────────────────────────────────────────────────────────────

    public void salva() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("nomeFamiglia", nomeFamiglia);
            root.addProperty("fotoNomeFile", fotoNomeFile);

            JsonArray ra = new JsonArray();
            for (RuoloCustom r : ruoli) {
                JsonObject ro = new JsonObject();
                ro.addProperty("nome",   r.getNome());
                ro.addProperty("emoji",  r.getEmoji());
                ro.addProperty("colore", r.getColore());
                ra.add(ro);
            }
            root.add("ruoli", ra);

            JsonArray ma = new JsonArray();
            for (Membro m : membri) {
                JsonObject mo = new JsonObject();
                mo.addProperty("nome",         m.getNome());
                mo.addProperty("ruoloNome",    m.getRuolo().getNome());
                mo.addProperty("stato",        m.getStato().name());
                mo.addProperty("nota",         m.getNota());
                mo.addProperty("dataIngresso", m.getDataIngresso());
                ma.add(mo);
            }
            root.add("membri", ma);

            // Scrittura atomica: scrivi su file temporaneo e poi rinomina
            Files.createDirectories(configDir);
            Path tmpPath = configDir.resolve("famiglia_data.json.tmp");
            try (Writer w = new FileWriter(tmpPath.toFile())) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
            Files.move(tmpPath, savePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore salvataggio dati famiglia", e);
        }
    }

    public void carica() {
        if (!Files.exists(savePath)) return;
        try (Reader r = new FileReader(savePath.toFile())) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            nomeFamiglia = root.has("nomeFamiglia") ? root.get("nomeFamiglia").getAsString() : "La Famiglia";
            fotoNomeFile = root.has("fotoNomeFile") ? root.get("fotoNomeFile").getAsString() : "";

            ruoli.clear();
            if (root.has("ruoli")) {
                for (JsonElement el : root.getAsJsonArray("ruoli")) {
                    try {
                        JsonObject ro = el.getAsJsonObject();
                        ruoli.add(new RuoloCustom(ro.get("nome").getAsString(),
                                                  ro.get("emoji").getAsString(),
                                                  ro.get("colore").getAsInt()));
                    } catch (Exception e) {
                        FamigliaMod.LOGGER.warn("Ruolo corrotto nel salvataggio, ignorato", e);
                    }
                }
            }

            membri.clear();
            if (root.has("membri")) {
                for (JsonElement el : root.getAsJsonArray("membri")) {
                    try {
                        JsonObject mo = el.getAsJsonObject();
                        String rn = mo.get("ruoloNome").getAsString();
                        RuoloCustom ruolo = ruoli.stream()
                            .filter(rx -> rx.getNome().equals(rn)).findFirst()
                            .orElse(ruoli.isEmpty() ? new RuoloCustom("?", "❓", 0xFFFFFFFF) : ruoli.get(0));
                        Membro m = new Membro(mo.get("nome").getAsString(), ruolo);
                        m.setStato(Membro.Stato.valueOf(mo.get("stato").getAsString()));
                        m.setNota(mo.has("nota") ? mo.get("nota").getAsString() : "");
                        m.setDataIngresso(mo.has("dataIngresso") ? mo.get("dataIngresso").getAsLong() : System.currentTimeMillis());
                        membri.add(m);
                    } catch (Exception e) {
                        FamigliaMod.LOGGER.warn("Membro corrotto nel salvataggio, ignorato", e);
                    }
                }
            }
        } catch (Exception e) {
            FamigliaMod.LOGGER.error("Errore caricamento dati famiglia", e);
        }
    }

    private void caricaDatiDemo() {
        if (ruoli.isEmpty()) ruoli = RuoloCustom.defaults();
        RuoloCustom[] r = ruoli.toArray(new RuoloCustom[0]);
        if (r.length == 0) return;
        addDemo("Don Salvatore",       r[0],                        Membro.Stato.IN_PIAZZA,   "Non si avvicina nessuno senza permesso");
        addDemo("Ciro 'o Milionario",  r[Math.min(1, r.length-1)],  Membro.Stato.ONLINE,      "");
        addDemo("Gennaro Savastano",   r[Math.min(2, r.length-1)],  Membro.Stato.IN_GUARDIA,  "Zona Vele");
        addDemo("Tonino 'o Pazzo",     r[Math.min(3, r.length-1)],  Membro.Stato.ONLINE,      "");
        addDemo("Pisellino",           r[Math.min(5, r.length-1)],  Membro.Stato.IN_GUARDIA,  "Tetto palazzina B");
        addDemo("Enzuccio",            r[Math.min(4, r.length-1)],  Membro.Stato.IN_PIAZZA,   "Angolo via Toledo");
        salva();
    }

    private void addDemo(String nome, RuoloCustom ruolo, Membro.Stato stato, String nota) {
        Membro m = new Membro(nome, ruolo);
        m.setStato(stato);
        m.setNota(nota);
        membri.add(m);
    }
}
