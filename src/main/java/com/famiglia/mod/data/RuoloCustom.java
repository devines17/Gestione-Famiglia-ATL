package com.famiglia.mod.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Ruolo completamente customizzabile: nome, emoji e colore hex scelti dal giocatore.
 * Sostituisce l'enum statico precedente.
 */
public class RuoloCustom {

    private String nome;       // es. "Capodecina"
    private String emoji;      // es. "⚔️"
    private int colore;        // ARGB packed, es. 0xFFFF4444

    public RuoloCustom(String nome, String emoji, int colore) {
        this.nome   = nome;
        this.emoji  = emoji;
        this.colore = colore;
    }

    /** Etichetta mostrata in GUI: "⚔️ Capodecina" */
    public String getEtichetta() {
        return emoji + " " + nome;
    }

    public String getNome()        { return nome; }
    public void   setNome(String n){ this.nome = n; }

    public String getEmoji()         { return emoji; }
    public void   setEmoji(String e) { this.emoji = e; }

    public int  getColore()          { return colore; }
    public void setColore(int c)     { this.colore = c; }

    // ── Default roles (used when no saved data exists) ───────────────────────

    public static List<RuoloCustom> defaults() {
        List<RuoloCustom> list = new ArrayList<>();
        list.add(new RuoloCustom("Boss",       "👑", 0xFFFFD700));
        list.add(new RuoloCustom("Underboss",  "🥇", 0xFFFFA500));
        list.add(new RuoloCustom("Capodecina", "⚔️", 0xFFFF4444));
        list.add(new RuoloCustom("Soldato",    "🔫", 0xFFAAAAAA));
        list.add(new RuoloCustom("Palo",       "👁", 0xFF88CCFF));
        list.add(new RuoloCustom("Vedetta",    "🗼", 0xFF44FF88));
        list.add(new RuoloCustom("Picciotto",  "🤝", 0xFFCCCCCC));
        return list;
    }

    @Override
    public String toString() { return getEtichetta(); }
}
