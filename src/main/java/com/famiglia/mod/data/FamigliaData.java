package com.famiglia.mod.data;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

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

    public static FamigliaData getInstance() {
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
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            fotoTextureId = id;
        } catch (Exception e) { e.printStackTrace(); fotoTextureId = null; }
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
    public void rimuoviRuolo(RuoloCustom r)  {
        RuoloCustom fallback = ruoli.size() > 1
            ? ruoli.stream().filter(x -> x != r).findFirst().orElse(r)
            : r;
        for (Membro m : membri) if (m.getRuolo() == r) m.setRuolo(fallback);
        ruoli.remove(r);
        salva();
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

            Files.createDirectories(configDir);
            try (Writer w = new FileWriter(savePath.toFile())) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void carica() {
        if (!savePath.toFile().exists()) return;
        try (Reader r = new FileReader(savePath.toFile())) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            nomeFamiglia = root.has("nomeFamiglia") ? root.get("nomeFamiglia").getAsString() : "La Famiglia";
            fotoNomeFile = root.has("fotoNomeFile") ? root.get("fotoNomeFile").getAsString() : "";

            ruoli.clear();
            if (root.has("ruoli")) {
                for (JsonElement el : root.getAsJsonArray("ruoli")) {
                    JsonObject ro = el.getAsJsonObject();
                    ruoli.add(new RuoloCustom(ro.get("nome").getAsString(),
                                              ro.get("emoji").getAsString(),
                                              ro.get("colore").getAsInt()));
                }
            }

            membri.clear();
            if (root.has("membri")) {
                for (JsonElement el : root.getAsJsonArray("membri")) {
                    JsonObject mo = el.getAsJsonObject();
                    String rn = mo.get("ruoloNome").getAsString();
                    RuoloCustom ruolo = ruoli.stream()
                        .filter(rx -> rx.getNome().equals(rn)).findFirst()
                        .orElse(ruoli.isEmpty() ? new RuoloCustom("?","❓",0xFFFFFFFF) : ruoli.get(0));
                    Membro m = new Membro(mo.get("nome").getAsString(), ruolo);
                    m.setStato(Membro.Stato.valueOf(mo.get("stato").getAsString()));
                    m.setNota(mo.get("nota").getAsString());
                    m.setDataIngresso(mo.get("dataIngresso").getAsLong());
                    membri.add(m);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void caricaDatiDemo() {
        if (ruoli.isEmpty()) ruoli = RuoloCustom.defaults();
        RuoloCustom[] r = ruoli.toArray(new RuoloCustom[0]);
        addDemo("Don Salvatore",       r[0], Membro.Stato.IN_PIAZZA,  "Non si avvicina nessuno senza permesso");
        addDemo("Ciro 'o Milionario",  r.length>1?r[1]:r[0], Membro.Stato.ONLINE, "");
        addDemo("Gennaro Savastano",   r.length>2?r[2]:r[0], Membro.Stato.IN_GUARDIA, "Zona Vele");
        addDemo("Tonino 'o Pazzo",     r.length>3?r[3]:r[0], Membro.Stato.ONLINE, "");
        addDemo("Pisellino",           r.length>5?r[5]:r[0], Membro.Stato.IN_GUARDIA, "Tetto palazzina B");
        addDemo("Enzuccio",            r.length>4?r[4]:r[0], Membro.Stato.IN_PIAZZA,  "Angolo via Toledo");
        salva();
    }

    private void addDemo(String nome, RuoloCustom ruolo, Membro.Stato stato, String nota) {
        Membro m = new Membro(nome, ruolo);
        m.setStato(stato); m.setNota(nota); membri.add(m);
    }
}
