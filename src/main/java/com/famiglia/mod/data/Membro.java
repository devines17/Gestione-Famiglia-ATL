package com.famiglia.mod.data;

public class Membro {

    public enum Stato {
        ONLINE    ("● Online",    0xFF00FF00),
        OFFLINE   ("○ Offline",   0xFF888888),
        IN_PIAZZA ("◆ In Piazza", 0xFFFFAA00),
        IN_GUARDIA("▲ In Guardia",0xFFFF4444),
        LATITANTE ("? Latitante", 0xFFFF00FF);

        public final String etichetta;
        public final int    colore;
        Stato(String etichetta, int colore) { this.etichetta = etichetta; this.colore = colore; }
    }

    private String      nome;
    private RuoloCustom ruolo;
    private Stato       stato;
    private String      nota;
    private long        dataIngresso;

    public Membro(String nome, RuoloCustom ruolo) {
        this.nome = nome; this.ruolo = ruolo;
        this.stato = Stato.OFFLINE; this.nota = "";
        this.dataIngresso = System.currentTimeMillis();
    }

    public String      getNome()               { return nome; }
    public void        setNome(String n)       { this.nome = n; }
    public RuoloCustom getRuolo()              { return ruolo; }
    public void        setRuolo(RuoloCustom r) { this.ruolo = r; }
    public Stato       getStato()              { return stato; }
    public void        setStato(Stato s)       { this.stato = s; }
    public String      getNota()               { return nota; }
    public void        setNota(String n)       { this.nota = n; }
    public long        getDataIngresso()       { return dataIngresso; }
    public void        setDataIngresso(long t) { this.dataIngresso = t; }
}
