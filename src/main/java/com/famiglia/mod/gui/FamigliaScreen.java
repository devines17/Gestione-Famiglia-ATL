package com.famiglia.mod.gui;

import com.famiglia.mod.client.FamigliaClientMod;
import com.famiglia.mod.data.FamigliaData;
import com.famiglia.mod.data.Membro;
import com.famiglia.mod.data.RuoloCustom;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * GUI principale della Famiglia.
 *
 * TAB 0 – Membri        : lista, aggiungi, modifica, rimuovi
 * TAB 1 – Ruoli         : visualizza, aggiungi, modifica, rimuovi ruoli custom
 * TAB 2 – Profilo       : nome famiglia, foto (selezione da file nella cartella config)
 *
 * Keybind modificabile da Opzioni → Controlli → Famiglia Mod
 */
public class FamigliaScreen extends Screen {

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int W  = 340;
    private static final int H  = 240;
    private static final int RH = 18;   // row height
    private static final int VR = 8;    // visible rows
    private static final int TAB_H = 16;

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final int C_BG        = 0xFF0A0A0A;
    private static final int C_PANEL     = 0xFF141414;
    private static final int C_HEADER    = 0xFF1A1A1A;
    private static final int C_ROW_EVEN  = 0xFF111111;
    private static final int C_ROW_ODD   = 0xFF0E0E0E;
    private static final int C_ROW_SEL   = 0xFF2A1A00;
    private static final int C_BORDER    = 0xFF8B6914;
    private static final int C_GOLD      = 0xFFFFD700;
    private static final int C_GOLD_DIM  = 0xFFAA9020;
    private static final int C_TEXT      = 0xFFDDDDDD;
    private static final int C_DIM       = 0xFF888888;
    private static final int C_TAB_ACT   = 0xFF1E1600;
    private static final int C_TAB_INACT = 0xFF0D0D0D;

    // ── State ─────────────────────────────────────────────────────────────────
    private final FamigliaData data = FamigliaData.getInstance();
    private int tab = 0;           // 0=Membri 1=Ruoli 2=Profilo
    private int px, py;            // panel origin

    // ── Tab 0 – Membri ────────────────────────────────────────────────────────
    private int    membriSel    = -1;
    private int    membriScroll = 0;
    private boolean membriAdd   = false;
    private boolean membriEdit  = false;

    private TextFieldWidget addNomeField;
    private int             addRuoloIdx   = 0;

    private TextFieldWidget editNotaField;
    private int             editRuoloIdx  = 0;
    private int             editStatoIdx  = 0;

    // ── Tab 1 – Ruoli ─────────────────────────────────────────────────────────
    private int     ruoliSel    = -1;
    private boolean ruoliAdd    = false;
    private boolean ruoliEdit   = false;

    private TextFieldWidget ruoloNomeField;
    private TextFieldWidget ruoloEmojiField;
    private TextFieldWidget ruoloColoreField;   // hex string es. "FF4444"

    // ── Tab 2 – Profilo ───────────────────────────────────────────────────────
    private TextFieldWidget famNomeField;
    private int             fotoIdx      = 0;  // indice nell'elenco immagini
    private List<String>    fotoList;

    // ─────────────────────────────────────────────────────────────────────────

    public FamigliaScreen() {
        super(Text.literal("Famiglia"));
    }

    @Override
    protected void init() {
        px = (width  - W) / 2;
        py = (height - H) / 2;
        clearChildren();

        // ── Tab bar ──────────────────────────────────────────────────────────
        String[] tabNames = {"👥 Membri", "⚔️ Ruoli", "🏠 Profilo"};
        int tabW = W / tabNames.length;
        for (int i = 0; i < tabNames.length; i++) {
            final int fi = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(tabNames[i]), btn -> {
                tab = fi;
                membriAdd = membriEdit = ruoliAdd = ruoliEdit = false;
                clearChildren(); init();
            }).dimensions(px + i * tabW, py + TAB_H, tabW, 14).build());
        }

        // ── Close button ─────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("✖"), btn -> onClose())
                .dimensions(px + W - 14, py + 1, 12, 12).build());

        if      (tab == 0) initTab0();
        else if (tab == 1) initTab1();
        else               initTab2();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB 0 – MEMBRI
    // ══════════════════════════════════════════════════════════════════════════

    private void initTab0() {
        int bodyY = py + TAB_H + 16;
        int btnY  = py + H - 18;

        if (membriAdd) {
            // add form
            addNomeField = new TextFieldWidget(textRenderer,
                    px + 8, bodyY + 8, W - 16, 14, Text.literal("nome"));
            addNomeField.setPlaceholder(Text.literal("Nome membro..."));
            addNomeField.setMaxLength(28);
            addDrawableChild(addNomeField);

            cycleButton(px + 8,           bodyY + 30, "◀", () -> {
                int n = data.getRuoli().size(); if (n>0) addRuoloIdx = (addRuoloIdx - 1 + n) % n; });
            cycleButton(px + W - 24,      bodyY + 30, "▶", () -> {
                int n = data.getRuoli().size(); if (n>0) addRuoloIdx = (addRuoloIdx + 1) % n; });

            confirmCancel(btnY,
                () -> {
                    String nome = FamigliaData.sanitize(addNomeField.getText().trim());
                    List<RuoloCustom> r = data.getRuoli();
                    if (!nome.isEmpty() && !r.isEmpty())
                        data.aggiungiMembro(new Membro(nome, r.get(Math.min(addRuoloIdx, r.size()-1))));
                    membriAdd = false; clearChildren(); init();
                },
                () -> { membriAdd = false; clearChildren(); init(); });
            return;
        }

        if (membriEdit && membriSel >= 0 && membriSel < data.getMembri().size()) {
            Membro m = data.getMembri().get(membriSel);
            int fy = bodyY + 8;

            // ruolo cycle
            cycleButton(px + 8,      fy,      "◀", () -> { int n=data.getRuoli().size(); if(n>0) editRuoloIdx=(editRuoloIdx-1+n)%n; });
            cycleButton(px + W - 24, fy,      "▶", () -> { int n=data.getRuoli().size(); if(n>0) editRuoloIdx=(editRuoloIdx+1)%n; });
            // stato cycle
            int nStati = Membro.Stato.values().length;
            cycleButton(px + 8,      fy + 20, "◀", () -> editStatoIdx = (editStatoIdx - 1 + nStati) % nStati);
            cycleButton(px + W - 24, fy + 20, "▶", () -> editStatoIdx = (editStatoIdx + 1) % nStati);

            editNotaField = new TextFieldWidget(textRenderer,
                    px + 8, fy + 42, W - 16, 14, Text.literal("nota"));
            editNotaField.setText(m.getNota());
            editNotaField.setMaxLength(48);
            addDrawableChild(editNotaField);

            confirmCancel(btnY,
                () -> {
                    List<RuoloCustom> r = data.getRuoli();
                    if (!r.isEmpty()) m.setRuolo(r.get(Math.min(editRuoloIdx, r.size()-1)));
                    m.setStato(Membro.Stato.values()[editStatoIdx]);
                    m.setNota(FamigliaData.sanitize(editNotaField.getText().trim()));
                    data.salva(); membriEdit = false; clearChildren(); init();
                },
                () -> { membriEdit = false; clearChildren(); init(); });
            return;
        }

        // ── main list ────────────────────────────────────────────────────────
        int bw = 62, gap = 4, total = bw * 3 + gap * 2;
        int bx = px + (W - total) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Aggiungi"), btn -> {
            membriAdd = true; addRuoloIdx = 0; clearChildren(); init();
        }).dimensions(bx, btnY, bw, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✎ Modifica"), btn -> {
            if (membriSel >= 0 && membriSel < data.getMembri().size()) {
                Membro m = data.getMembri().get(membriSel);
                editRuoloIdx = Math.max(0, data.getRuoli().indexOf(m.getRuolo()));
                editStatoIdx = m.getStato().ordinal();
                membriEdit = true; clearChildren(); init();
            }
        }).dimensions(bx + bw + gap, btnY, bw, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✖ Rimuovi"), btn -> {
            if (membriSel >= 0 && membriSel < data.getMembri().size()) {
                data.rimuoviMembro(data.getMembri().get(membriSel));
                membriSel = -1; clearChildren(); init();
            }
        }).dimensions(bx + (bw + gap) * 2, btnY, bw, 14).build());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB 1 – RUOLI
    // ══════════════════════════════════════════════════════════════════════════

    private void initTab1() {
        int bodyY = py + TAB_H + 16;
        int btnY  = py + H - 18;

        if (ruoliAdd || (ruoliEdit && ruoliSel >= 0 && ruoliSel < data.getRuoli().size())) {
            int fy = bodyY + 6;

            ruoloNomeField = new TextFieldWidget(textRenderer, px+8, fy,    W-16, 14, Text.literal("nome"));
            ruoloEmojiField= new TextFieldWidget(textRenderer, px+8, fy+18, 40,   14, Text.literal("emoji"));
            ruoloColoreField=new TextFieldWidget(textRenderer, px+60,fy+18, W-68, 14, Text.literal("colore"));

            ruoloNomeField.setMaxLength(20);
            ruoloEmojiField.setMaxLength(4);
            ruoloColoreField.setMaxLength(8);

            if (ruoliEdit) {
                RuoloCustom r = data.getRuoli().get(ruoliSel);
                ruoloNomeField.setText(r.getNome());
                ruoloEmojiField.setText(r.getEmoji());
                ruoloColoreField.setText(Integer.toHexString(r.getColore() & 0xFFFFFF).toUpperCase());
            } else {
                ruoloNomeField.setPlaceholder(Text.literal("es. Consigliere"));
                ruoloEmojiField.setPlaceholder(Text.literal("🗡️"));
                ruoloColoreField.setPlaceholder(Text.literal("FF8800  (hex RGB)"));
            }
            addDrawableChild(ruoloNomeField);
            addDrawableChild(ruoloEmojiField);
            addDrawableChild(ruoloColoreField);

            confirmCancel(btnY,
                () -> {
                    String nome  = FamigliaData.sanitize(ruoloNomeField.getText().trim());
                    String emoji = FamigliaData.sanitize(ruoloEmojiField.getText().trim());
                    String hex   = FamigliaData.sanitize(ruoloColoreField.getText().trim());
                    if (nome.isEmpty()) { ruoliAdd = ruoliEdit = false; clearChildren(); init(); return; }
                    int colore;
                    try { colore = (int)(0xFF000000L | Long.parseLong(hex.replace("#",""), 16)); }
                    catch (Exception e) { colore = 0xFFAAAAAA; }
                    if (ruoliEdit) {
                        RuoloCustom r = data.getRuoli().get(ruoliSel);
                        r.setNome(nome); r.setEmoji(emoji.isEmpty()?"◆":emoji); r.setColore(colore);
                        data.salva();
                    } else {
                        data.aggiungiRuolo(new RuoloCustom(nome, emoji.isEmpty()?"◆":emoji, colore));
                    }
                    ruoliAdd = ruoliEdit = false; clearChildren(); init();
                },
                () -> { ruoliAdd = ruoliEdit = false; clearChildren(); init(); });
            return;
        }

        // ── main list ────────────────────────────────────────────────────────
        int bw = 62, gap = 4, total = bw * 3 + gap * 2;
        int bx = px + (W - total) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Nuovo"), btn -> {
            ruoliAdd = true; clearChildren(); init();
        }).dimensions(bx, btnY, bw, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✎ Modifica"), btn -> {
            if (ruoliSel >= 0 && ruoliSel < data.getRuoli().size()) {
                ruoliEdit = true; clearChildren(); init();
            }
        }).dimensions(bx + bw + gap, btnY, bw, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✖ Elimina"), btn -> {
            if (ruoliSel >= 0 && ruoliSel < data.getRuoli().size()) {
                data.rimuoviRuolo(data.getRuoli().get(ruoliSel));
                ruoliSel = -1; clearChildren(); init();
            }
        }).dimensions(bx + (bw + gap) * 2, btnY, bw, 14).build());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB 2 – PROFILO
    // ══════════════════════════════════════════════════════════════════════════

    private void initTab2() {
        int bodyY = py + TAB_H + 16;
        fotoList = data.getImmaginiDisponibili();

        // Nome famiglia
        famNomeField = new TextFieldWidget(textRenderer,
                px + 8, bodyY + 8, W - 16, 14, Text.literal("nome famiglia"));
        famNomeField.setText(data.getNomeFamiglia());
        famNomeField.setMaxLength(32);
        addDrawableChild(famNomeField);

        // Foto: ciclo su file disponibili
        cycleButton(px + 8,      bodyY + 36, "◀", () -> {
            if (!fotoList.isEmpty()) fotoIdx = (fotoIdx - 1 + fotoList.size()) % fotoList.size(); });
        cycleButton(px + W - 24, bodyY + 36, "▶", () -> {
            if (!fotoList.isEmpty()) fotoIdx = (fotoIdx + 1) % fotoList.size(); });

        // Nessuna foto
        addDrawableChild(ButtonWidget.builder(Text.literal("✖ Nessuna foto"), btn -> {
            data.setFotoNomeFile(""); clearChildren(); init();
        }).dimensions(px + 8, bodyY + 120, 100, 14).build());

        // Salva
        addDrawableChild(ButtonWidget.builder(Text.literal("✔ Salva"), btn -> {
            String famName = FamigliaData.sanitize(famNomeField.getText().trim());
            data.setNomeFamiglia(famName.isEmpty() ? "La Famiglia" : famName);
            if (!fotoList.isEmpty()) data.setFotoNomeFile(fotoList.get(fotoIdx));
            clearChildren(); init();
        }).dimensions(px + W - 80, py + H - 18, 72, 14).build());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDER
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xCC000000, 0xDD000000);

        // Outer gold border
        border(ctx, px-2, py-2, W+4, H+4, C_GOLD, 2);
        ctx.fill(px, py, px+W, py+H, C_PANEL);

        // Title bar
        ctx.fill(px, py, px+W, py+TAB_H, C_HEADER);
        border(ctx, px, py, W, TAB_H, C_BORDER, 1);
        String title = "✦  " + data.getNomeFamiglia().toUpperCase() + "  ✦";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(title), px + W/2, py+4, C_GOLD);

        // Tab highlight
        int tabW = W / 3;
        ctx.fill(px + tab*tabW, py+TAB_H, px + tab*tabW + tabW, py+TAB_H+14, C_TAB_ACT);
        border(ctx, px + tab*tabW, py+TAB_H, tabW, 14, C_BORDER, 1);

        if      (tab == 0) renderTab0(ctx, mouseX, mouseY);
        else if (tab == 1) renderTab1(ctx, mouseX, mouseY);
        else               renderTab2(ctx);

        // Keybind hint bottom-right
        String hint = "[" + FamigliaClientMod.openFamigliaKey.getBoundKeyLocalizedText().getString() + "] chiudi";
        ctx.drawText(textRenderer, Text.literal(hint), px + W - textRenderer.getWidth(hint) - 4, py+H-9, C_DIM, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Tab 0 render ─────────────────────────────────────────────────────────

    private void renderTab0(DrawContext ctx, int mx, int my) {
        int bodyY = py + TAB_H + 16;

        if (membriAdd) {
            ctx.drawText(textRenderer, Text.literal("AGGIUNGI MEMBRO"), px+8, bodyY, C_GOLD, false);
            ctx.drawText(textRenderer, Text.literal("Nome:"), px+8, bodyY+26, C_DIM, false);
            // Ruolo preview
            List<RuoloCustom> r = data.getRuoli();
            if (!r.isEmpty()) {
                RuoloCustom rc = r.get(Math.min(addRuoloIdx, r.size()-1));
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(rc.getEtichetta()).withColor(rc.getColore()),
                        px+W/2, bodyY+32, 0xFFFFFFFF);
            }
            return;
        }
        if (membriEdit && membriSel >= 0 && membriSel < data.getMembri().size()) {
            Membro m = data.getMembri().get(membriSel);
            ctx.drawText(textRenderer, Text.literal("MODIFICA: " + m.getNome().toUpperCase()), px+8, bodyY, C_GOLD, false);

            List<RuoloCustom> r = data.getRuoli();
            if (!r.isEmpty()) {
                RuoloCustom rc = r.get(Math.min(editRuoloIdx, r.size()-1));
                ctx.drawText(textRenderer, Text.literal("Ruolo:"), px+8, bodyY+8, C_DIM, false);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(rc.getEtichetta()).withColor(rc.getColore()), px+W/2, bodyY+10, 0xFFFFFFFF);
            }
            Membro.Stato stato = Membro.Stato.values()[editStatoIdx];
            ctx.drawText(textRenderer, Text.literal("Stato:"), px+8, bodyY+20, C_DIM, false);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(stato.etichetta).withColor(stato.colore), px+W/2, bodyY+28, 0xFFFFFFFF);

            ctx.drawText(textRenderer, Text.literal("Nota:"), px+8, bodyY+40, C_DIM, false);
            return;
        }

        // List headers
        int hY = bodyY;
        ctx.fill(px, hY, px+W, hY+10, 0xFF1E1600);
        ctx.drawText(textRenderer, Text.literal("MEMBRO"),  px+6,   hY+1, C_GOLD_DIM, false);
        ctx.drawText(textRenderer, Text.literal("RUOLO"),   px+140, hY+1, C_GOLD_DIM, false);
        ctx.drawText(textRenderer, Text.literal("STATO"),   px+235, hY+1, C_GOLD_DIM, false);

        List<Membro> ml = data.getMembri();
        int listY = hY + 11;
        int end   = Math.min(membriScroll + VR, ml.size());
        for (int i = membriScroll; i < end; i++) {
            Membro m   = ml.get(i);
            int    ry  = listY + (i - membriScroll) * RH;
            int    bg  = (i == membriSel) ? C_ROW_SEL : (i%2==0 ? C_ROW_EVEN : C_ROW_ODD);
            ctx.fill(px, ry, px+W, ry+RH-1, bg);
            if (mx>=px && mx<px+W && my>=ry && my<ry+RH-1)
                ctx.fill(px, ry, px+W, ry+RH-1, 0x22FFFFFF);
            ctx.drawText(textRenderer, Text.literal(m.getNome()),                     px+6,   ry+4, C_TEXT,           false);
            ctx.drawText(textRenderer, Text.literal(m.getRuolo().getEtichetta()).withColor(m.getRuolo().getColore()), px+140, ry+4, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, Text.literal(m.getStato().etichetta).withColor(m.getStato().colore),          px+235, ry+4, 0xFFFFFFFF, false);
            if (!m.getNota().isEmpty())
                ctx.drawText(textRenderer, Text.literal("✉"), px+W-12, ry+4, C_GOLD_DIM, false);
        }
        if (membriScroll > 0)
            ctx.drawText(textRenderer, Text.literal("▲"), px+W-10, listY,              C_GOLD_DIM, false);
        if (membriScroll + VR < ml.size())
            ctx.drawText(textRenderer, Text.literal("▼"), px+W-10, listY+VR*RH-RH,    C_GOLD_DIM, false);

        // Stats
        long active = ml.stream().filter(m -> m.getStato() != Membro.Stato.OFFLINE).count();
        ctx.drawText(textRenderer,
                Text.literal("Membri: " + ml.size() + "  •  Attivi: " + active),
                px+6, py+H-22, C_DIM, false);
    }

    // ── Tab 1 render ─────────────────────────────────────────────────────────

    private void renderTab1(DrawContext ctx, int mx, int my) {
        int bodyY = py + TAB_H + 16;

        if (ruoliAdd) {
            ctx.drawText(textRenderer, Text.literal("NUOVO RUOLO"), px+8, bodyY, C_GOLD, false);
            ctx.drawText(textRenderer, Text.literal("Nome:"),   px+8, bodyY+4,  C_DIM, false);
            ctx.drawText(textRenderer, Text.literal("Emoji:"),  px+8, bodyY+22, C_DIM, false);
            ctx.drawText(textRenderer, Text.literal("Colore (hex RGB):"), px+52, bodyY+22, C_DIM, false);
            return;
        }
        if (ruoliEdit && ruoliSel >= 0 && ruoliSel < data.getRuoli().size()) {
            ctx.drawText(textRenderer, Text.literal("MODIFICA RUOLO"), px+8, bodyY, C_GOLD, false);
            ctx.drawText(textRenderer, Text.literal("Nome:"),   px+8, bodyY+4,  C_DIM, false);
            ctx.drawText(textRenderer, Text.literal("Emoji:"),  px+8, bodyY+22, C_DIM, false);
            ctx.drawText(textRenderer, Text.literal("Colore (hex RGB):"), px+52, bodyY+22, C_DIM, false);
            return;
        }

        // Roles list
        int hY = bodyY;
        ctx.fill(px, hY, px+W, hY+10, 0xFF1E1600);
        ctx.drawText(textRenderer, Text.literal("EMOJI"), px+6,  hY+1, C_GOLD_DIM, false);
        ctx.drawText(textRenderer, Text.literal("NOME"),  px+40, hY+1, C_GOLD_DIM, false);
        ctx.drawText(textRenderer, Text.literal("COLORE HEX"), px+200, hY+1, C_GOLD_DIM, false);

        List<RuoloCustom> rl = data.getRuoli();
        int listY = hY + 11;
        for (int i = 0; i < Math.min(rl.size(), 9); i++) {
            RuoloCustom r  = rl.get(i);
            int         ry = listY + i * RH;
            int         bg = (i == ruoliSel) ? C_ROW_SEL : (i%2==0 ? C_ROW_EVEN : C_ROW_ODD);
            ctx.fill(px, ry, px+W, ry+RH-1, bg);
            if (mx>=px && mx<px+W && my>=ry && my<ry+RH-1)
                ctx.fill(px, ry, px+W, ry+RH-1, 0x22FFFFFF);
            ctx.drawText(textRenderer, Text.literal(r.getEmoji()),                    px+6,   ry+4, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, Text.literal(r.getNome()).withColor(r.getColore()), px+40, ry+4, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer,
                    Text.literal("#" + Integer.toHexString(r.getColore()&0xFFFFFF).toUpperCase()),
                    px+200, ry+4, r.getColore(), false);
        }
    }

    // ── Tab 2 render ─────────────────────────────────────────────────────────

    private void renderTab2(DrawContext ctx) {
        int bodyY = py + TAB_H + 16;

        ctx.drawText(textRenderer, Text.literal("Nome famiglia:"), px+8, bodyY, C_GOLD, false);

        // Foto
        ctx.drawText(textRenderer, Text.literal("Foto profilo:"), px+8, bodyY+28, C_GOLD, false);

        // Photo preview box (64×64)
        int imgX = px + (W - 64) / 2;
        int imgY = bodyY + 40;
        ctx.fill(imgX-1, imgY-1, imgX+65, imgY+65, C_BORDER);
        ctx.fill(imgX, imgY, imgX+64, imgY+64, 0xFF050505);

        Identifier tex = data.getFotoTexture();
        if (tex != null) {
            ctx.drawTexture(tex, imgX, imgY, 0, 0, 64, 64, 64, 64);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Nessuna foto"), imgX+32, imgY+28, C_DIM);
        }

        // Current filename or hint
        String nomeFile = fotoList != null && !fotoList.isEmpty()
                ? fotoList.get(Math.min(fotoIdx, fotoList.size()-1))
                : (data.getFotoNomeFile().isEmpty() ? "Nessun file" : data.getFotoNomeFile());
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(nomeFile), px+W/2, imgY+68, C_DIM);

        // Hint cartella
        ctx.drawText(textRenderer,
                Text.literal("Metti PNG/JPG in: config/famiglia/"),
                px+8, bodyY+148, C_DIM, false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MOUSE & SCROLL
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (tab == 0 && !membriAdd && !membriEdit) {
            int listY = py + TAB_H + 27;
            if (mx >= px && mx < px+W) {
                List<Membro> ml = data.getMembri();
                for (int i = membriScroll; i < Math.min(membriScroll+VR, ml.size()); i++) {
                    int ry = listY + (i-membriScroll)*RH;
                    if (my >= ry && my < ry+RH-1) { membriSel = (membriSel==i)?-1:i; return true; }
                }
            }
        }
        if (tab == 1 && !ruoliAdd && !ruoliEdit) {
            int listY = py + TAB_H + 27;
            if (mx >= px && mx < px+W) {
                List<RuoloCustom> rl = data.getRuoli();
                for (int i = 0; i < Math.min(rl.size(), 9); i++) {
                    int ry = listY + i*RH;
                    if (my >= ry && my < ry+RH-1) { ruoliSel = (ruoliSel==i)?-1:i; return true; }
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (tab == 0 && !membriAdd && !membriEdit) {
            membriScroll -= (int) Math.signum(vAmt);
            membriScroll  = Math.max(0, Math.min(membriScroll, Math.max(0, data.getMembri().size()-VR)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            if (membriAdd||membriEdit||ruoliAdd||ruoliEdit) {
                membriAdd=membriEdit=ruoliAdd=ruoliEdit=false; clearChildren(); init(); return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean shouldPause() { return false; }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void border(DrawContext ctx, int x, int y, int w, int h, int col, int t) {
        ctx.fill(x,       y,       x+w,   y+t,   col);
        ctx.fill(x,       y+h-t,   x+w,   y+h,   col);
        ctx.fill(x,       y,       x+t,   y+h,   col);
        ctx.fill(x+w-t,   y,       x+w,   y+h,   col);
    }

    private void cycleButton(int x, int y, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> { action.run(); clearChildren(); init(); })
                .dimensions(x, y, 14, 12).build());
    }

    private void confirmCancel(int btnY, Runnable confirm, Runnable cancel) {
        addDrawableChild(ButtonWidget.builder(Text.literal("✔ Conferma"), btn -> confirm.run())
                .dimensions(px+16,    btnY, 88, 14).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("✖ Annulla"), btn -> cancel.run())
                .dimensions(px+W-104, btnY, 88, 14).build());
    }
}
