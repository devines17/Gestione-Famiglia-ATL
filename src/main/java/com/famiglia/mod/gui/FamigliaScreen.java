package com.famiglia.mod.gui;

import com.famiglia.mod.client.FamigliaClientMod;
import com.famiglia.mod.data.FamigliaData;
import com.famiglia.mod.data.Membro;
import com.famiglia.mod.data.RuoloCustom;
import net.minecraft.client.gl.RenderPipelines;
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
 * TAB 0 - Membri        : lista, aggiungi, modifica, rimuovi
 * TAB 1 - Ruoli         : visualizza, aggiungi, modifica, rimuovi ruoli custom
 * TAB 2 - Profilo       : nome famiglia, foto (selezione da file nella cartella config)
 *
 * Keybind modificabile da Opzioni -> Controlli -> Famiglia Mod
 */
public class FamigliaScreen extends Screen {

    // -- Layout ----------------------------------------------------------------
    private static final int W  = 360;
    private static final int H  = 260;
    private static final int RH = 20;   // row height
    private static final int VR = 8;    // visible rows
    private static final int TAB_H = 20;
    private static final int HEADER_H = 22;
    private static final int PAD = 10;

    // -- Palette ---------------------------------------------------------------
    private static final int C_BG         = 0xCC000000;
    private static final int C_BG2        = 0xDD000000;
    private static final int C_PANEL      = 0xFF101010;
    private static final int C_HEADER     = 0xFF181410;
    private static final int C_ROW_EVEN   = 0xFF151515;
    private static final int C_ROW_ODD    = 0xFF121212;
    private static final int C_ROW_SEL    = 0xFF2E1E00;
    private static final int C_ROW_HOVER  = 0x18FFFFFF;
    private static final int C_BORDER     = 0xFF8B6914;
    private static final int C_GOLD       = 0xFFFFD700;
    private static final int C_GOLD_DIM   = 0xFFAA9020;
    private static final int C_TEXT       = 0xFFDDDDDD;
    private static final int C_DIM        = 0xFF777777;
    private static final int C_CREDIT     = 0xFF444444;
    private static final int C_TAB_ACT    = 0xFF1E1600;
    private static final int C_TAB_INACT  = 0xFF0C0C0C;
    private static final int C_DIVIDER    = 0xFF2A2A2A;
    private static final int C_FORM_BG    = 0xFF0E0E0E;

    // -- State -----------------------------------------------------------------
    private final FamigliaData data = FamigliaData.getInstance();
    private int tab = 0;           // 0=Membri 1=Ruoli 2=Profilo
    private int px, py;            // panel origin

    // -- Tab 0 - Membri --------------------------------------------------------
    private int     membriSel    = -1;
    private int     membriScroll = 0;
    private boolean membriAdd    = false;
    private boolean membriEdit   = false;

    private TextFieldWidget addNomeField;
    private int             addRuoloIdx   = 0;

    private TextFieldWidget editNotaField;
    private int             editRuoloIdx  = 0;
    private int             editStatoIdx  = 0;

    // -- Tab 1 - Ruoli ---------------------------------------------------------
    private int     ruoliSel    = -1;
    private int     ruoliScroll = 0;
    private boolean ruoliAdd    = false;
    private boolean ruoliEdit   = false;

    private TextFieldWidget ruoloNomeField;
    private TextFieldWidget ruoloEmojiField;
    private TextFieldWidget ruoloColoreField;

    // -- Tab 2 - Profilo -------------------------------------------------------
    private TextFieldWidget famNomeField;
    private int             fotoIdx      = 0;
    private List<String>    fotoList;

    // --------------------------------------------------------------------------

    public FamigliaScreen() {
        super(Text.literal("Famiglia"));
    }

    @Override
    protected void init() {
        px = (width  - W) / 2;
        py = (height - H) / 2;
        clearChildren();

        // -- Tab bar -----------------------------------------------------------
        String[] tabNames = {"Membri", "Ruoli", "Profilo"};
        int tabW = W / tabNames.length;
        for (int i = 0; i < tabNames.length; i++) {
            final int fi = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(tabNames[i]), btn -> {
                tab = fi;
                membriAdd = membriEdit = ruoliAdd = ruoliEdit = false;
                clearChildren(); init();
            }).dimensions(px + i * tabW, py + HEADER_H, tabW, 16).build());
        }

        // -- Close button ------------------------------------------------------
        addDrawableChild(ButtonWidget.builder(Text.literal("X"), btn -> close())
                .dimensions(px + W - 16, py + 3, 14, 14).build());

        if      (tab == 0) initTab0();
        else if (tab == 1) initTab1();
        else               initTab2();
    }

    // ==========================================================================
    // TAB 0 - MEMBRI
    // ==========================================================================

    private void initTab0() {
        int bodyY = py + HEADER_H + TAB_H + 4;
        int btnY  = py + H - 22;

        if (membriAdd) {
            addNomeField = new TextFieldWidget(textRenderer,
                    px + PAD, bodyY + 22, W - PAD * 2, 16, Text.literal("nome"));
            addNomeField.setPlaceholder(Text.literal("Inserisci il nome..."));
            addNomeField.setMaxLength(28);
            addDrawableChild(addNomeField);

            cycleButton(px + PAD,          bodyY + 50, "<", () -> {
                int n = data.getRuoli().size(); if (n > 0) addRuoloIdx = (addRuoloIdx - 1 + n) % n; });
            cycleButton(px + W - PAD - 16, bodyY + 50, ">", () -> {
                int n = data.getRuoli().size(); if (n > 0) addRuoloIdx = (addRuoloIdx + 1) % n; });

            confirmCancel(btnY,
                () -> {
                    String nome = addNomeField.getText().trim();
                    List<RuoloCustom> r = data.getRuoli();
                    if (!nome.isEmpty() && !r.isEmpty()) {
                        addRuoloIdx = Math.min(addRuoloIdx, r.size() - 1);
                        data.aggiungiMembro(new Membro(nome, r.get(addRuoloIdx)));
                    }
                    membriAdd = false; clearChildren(); init();
                },
                () -> { membriAdd = false; clearChildren(); init(); });
            return;
        }

        if (membriEdit && membriSel >= 0 && membriSel < data.getMembri().size()) {
            Membro m = data.getMembri().get(membriSel);
            int fy = bodyY + 8;

            cycleButton(px + PAD,          fy,      "<", () -> { int n = data.getRuoli().size(); if (n > 0) editRuoloIdx = (editRuoloIdx - 1 + n) % n; });
            cycleButton(px + W - PAD - 16, fy,      ">", () -> { int n = data.getRuoli().size(); if (n > 0) editRuoloIdx = (editRuoloIdx + 1) % n; });
            int nStati = Membro.Stato.values().length;
            cycleButton(px + PAD,          fy + 24, "<", () -> editStatoIdx = (editStatoIdx - 1 + nStati) % nStati);
            cycleButton(px + W - PAD - 16, fy + 24, ">", () -> editStatoIdx = (editStatoIdx + 1) % nStati);

            editNotaField = new TextFieldWidget(textRenderer,
                    px + PAD, fy + 52, W - PAD * 2, 16, Text.literal("nota"));
            editNotaField.setText(m.getNota());
            editNotaField.setMaxLength(48);
            addDrawableChild(editNotaField);

            confirmCancel(btnY,
                () -> {
                    List<RuoloCustom> r = data.getRuoli();
                    if (!r.isEmpty()) {
                        editRuoloIdx = Math.min(editRuoloIdx, r.size() - 1);
                        m.setRuolo(r.get(editRuoloIdx));
                    }
                    editStatoIdx = Math.min(editStatoIdx, Membro.Stato.values().length - 1);
                    m.setStato(Membro.Stato.values()[editStatoIdx]);
                    m.setNota(editNotaField.getText().trim());
                    data.salva(); membriEdit = false; clearChildren(); init();
                },
                () -> { membriEdit = false; clearChildren(); init(); });
            return;
        }

        // -- main list buttons ------------------------------------------------
        int bw = 72, gap = 6, total = bw * 3 + gap * 2;
        int bx = px + (W - total) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Aggiungi"), btn -> {
            membriAdd = true; addRuoloIdx = 0; clearChildren(); init();
        }).dimensions(bx, btnY, bw, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Modifica"), btn -> {
            if (membriSel >= 0 && membriSel < data.getMembri().size()) {
                Membro m = data.getMembri().get(membriSel);
                editRuoloIdx = Math.max(0, data.getRuoli().indexOf(m.getRuolo()));
                editStatoIdx = m.getStato().ordinal();
                membriEdit = true; clearChildren(); init();
            }
        }).dimensions(bx + bw + gap, btnY, bw, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Rimuovi"), btn -> {
            if (membriSel >= 0 && membriSel < data.getMembri().size()) {
                data.rimuoviMembro(data.getMembri().get(membriSel));
                membriSel = -1;
                membriScroll = Math.max(0, Math.min(membriScroll, Math.max(0, data.getMembri().size() - VR)));
                clearChildren(); init();
            }
        }).dimensions(bx + (bw + gap) * 2, btnY, bw, 16).build());
    }

    // ==========================================================================
    // TAB 1 - RUOLI
    // ==========================================================================

    private void initTab1() {
        int bodyY = py + HEADER_H + TAB_H + 4;
        int btnY  = py + H - 22;

        if (ruoliAdd || (ruoliEdit && ruoliSel >= 0 && ruoliSel < data.getRuoli().size())) {
            int fy = bodyY + 20;

            ruoloNomeField   = new TextFieldWidget(textRenderer, px + PAD, fy,      W - PAD * 2, 16, Text.literal("nome"));
            ruoloEmojiField  = new TextFieldWidget(textRenderer, px + PAD, fy + 24, 50,          16, Text.literal("emoji"));
            ruoloColoreField = new TextFieldWidget(textRenderer, px + 70,  fy + 24, W - 80,      16, Text.literal("colore"));

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
                ruoloEmojiField.setPlaceholder(Text.literal("?"));
                ruoloColoreField.setPlaceholder(Text.literal("FF8800"));
            }
            addDrawableChild(ruoloNomeField);
            addDrawableChild(ruoloEmojiField);
            addDrawableChild(ruoloColoreField);

            confirmCancel(btnY,
                () -> {
                    String nome  = ruoloNomeField.getText().trim();
                    String emoji = ruoloEmojiField.getText().trim();
                    String hex   = ruoloColoreField.getText().trim().replace("#", "");
                    if (nome.isEmpty()) { ruoliAdd = ruoliEdit = false; clearChildren(); init(); return; }
                    int colore;
                    try {
                        if (hex.length() > 6) hex = hex.substring(0, 6);
                        colore = (int)(0xFF000000L | Long.parseLong(hex.isEmpty() ? "AAAAAA" : hex, 16));
                    } catch (Exception e) { colore = 0xFFAAAAAA; }
                    if (ruoliEdit) {
                        RuoloCustom r = data.getRuoli().get(ruoliSel);
                        r.setNome(nome); r.setEmoji(emoji.isEmpty() ? "*" : emoji); r.setColore(colore);
                        data.salva();
                    } else {
                        data.aggiungiRuolo(new RuoloCustom(nome, emoji.isEmpty() ? "*" : emoji, colore));
                    }
                    ruoliAdd = ruoliEdit = false; clearChildren(); init();
                },
                () -> { ruoliAdd = ruoliEdit = false; clearChildren(); init(); });
            return;
        }

        // -- main list buttons ------------------------------------------------
        int bw = 72, gap = 6, total = bw * 3 + gap * 2;
        int bx = px + (W - total) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("+ Nuovo"), btn -> {
            ruoliAdd = true; clearChildren(); init();
        }).dimensions(bx, btnY, bw, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Modifica"), btn -> {
            if (ruoliSel >= 0 && ruoliSel < data.getRuoli().size()) {
                ruoliEdit = true; clearChildren(); init();
            }
        }).dimensions(bx + bw + gap, btnY, bw, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Elimina"), btn -> {
            if (ruoliSel >= 0 && ruoliSel < data.getRuoli().size()) {
                if (data.rimuoviRuolo(data.getRuoli().get(ruoliSel))) {
                    ruoliSel = -1;
                    ruoliScroll = Math.max(0, Math.min(ruoliScroll, Math.max(0, data.getRuoli().size() - VR)));
                }
                clearChildren(); init();
            }
        }).dimensions(bx + (bw + gap) * 2, btnY, bw, 16).build());
    }

    // ==========================================================================
    // TAB 2 - PROFILO
    // ==========================================================================

    private void initTab2() {
        int bodyY = py + HEADER_H + TAB_H + 4;
        fotoList = data.getImmaginiDisponibili();
        if (fotoList.isEmpty()) {
            fotoIdx = 0;
        } else {
            fotoIdx = Math.min(fotoIdx, fotoList.size() - 1);
        }

        famNomeField = new TextFieldWidget(textRenderer,
                px + PAD, bodyY + 16, W - PAD * 2, 16, Text.literal("nome famiglia"));
        famNomeField.setText(data.getNomeFamiglia());
        famNomeField.setMaxLength(32);
        addDrawableChild(famNomeField);

        cycleButton(px + PAD,          bodyY + 46, "<", () -> {
            if (!fotoList.isEmpty()) fotoIdx = (fotoIdx - 1 + fotoList.size()) % fotoList.size(); });
        cycleButton(px + W - PAD - 16, bodyY + 46, ">", () -> {
            if (!fotoList.isEmpty()) fotoIdx = (fotoIdx + 1) % fotoList.size(); });

        addDrawableChild(ButtonWidget.builder(Text.literal("Nessuna foto"), btn -> {
            data.setFotoNomeFile(""); clearChildren(); init();
        }).dimensions(px + PAD, bodyY + 130, 110, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Salva"), btn -> {
            data.setNomeFamiglia(famNomeField.getText().trim().isEmpty()
                    ? "La Famiglia" : famNomeField.getText().trim());
            if (!fotoList.isEmpty()) {
                int idx = Math.min(fotoIdx, fotoList.size() - 1);
                data.setFotoNomeFile(fotoList.get(idx));
            }
            clearChildren(); init();
        }).dimensions(px + W - 90, py + H - 22, 80, 16).build());
    }

    // ==========================================================================
    // RENDER
    // ==========================================================================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, C_BG, C_BG2);

        border(ctx, px - 2, py - 2, W + 4, H + 4, C_GOLD, 2);
        ctx.fill(px, py, px + W, py + H, C_PANEL);

        // -- Header bar -------------------------------------------------------
        ctx.fill(px, py, px + W, py + HEADER_H, C_HEADER);
        border(ctx, px, py, W, HEADER_H, C_BORDER, 1);
        String title = "* " + data.getNomeFamiglia().toUpperCase() + " *";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(title), px + W / 2, py + 7, C_GOLD);

        // -- Tab highlight ----------------------------------------------------
        int tabW = W / 3;
        for (int i = 0; i < 3; i++) {
            int tx = px + i * tabW;
            ctx.fill(tx, py + HEADER_H, tx + tabW, py + HEADER_H + 16, i == tab ? C_TAB_ACT : C_TAB_INACT);
            if (i == tab) border(ctx, tx, py + HEADER_H, tabW, 16, C_BORDER, 1);
        }

        ctx.fill(px, py + HEADER_H + 16, px + W, py + HEADER_H + 17, C_DIVIDER);

        if      (tab == 0) renderTab0(ctx, mouseX, mouseY);
        else if (tab == 1) renderTab1(ctx, mouseX, mouseY);
        else               renderTab2(ctx);

        // -- Footer -----------------------------------------------------------
        String hint = "[" + FamigliaClientMod.openFamigliaKey.getBoundKeyLocalizedText().getString() + "] chiudi";
        ctx.drawText(textRenderer, Text.literal(hint),
                px + W - textRenderer.getWidth(hint) - 6, py + H - 10, C_DIM, false);

        ctx.drawText(textRenderer, Text.literal("by devines"),
                px + 4, py + H - 10, C_CREDIT, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // -- Tab 0 render ---------------------------------------------------------

    private void renderTab0(DrawContext ctx, int mx, int my) {
        int bodyY = py + HEADER_H + TAB_H + 4;

        if (membriAdd) {
            ctx.fill(px + 4, bodyY - 2, px + W - 4, bodyY + 80, C_FORM_BG);
            border(ctx, px + 4, bodyY - 2, W - 8, 82, C_BORDER, 1);

            ctx.drawText(textRenderer, Text.literal(">> AGGIUNGI MEMBRO"), px + PAD, bodyY + 2, C_GOLD, true);
            ctx.drawText(textRenderer, Text.literal("Nome:"), px + PAD, bodyY + 14, C_DIM, false);

            List<RuoloCustom> r = data.getRuoli();
            if (!r.isEmpty()) {
                int idx = Math.min(addRuoloIdx, r.size() - 1);
                RuoloCustom rc = r.get(idx);
                ctx.drawText(textRenderer, Text.literal("Ruolo:"), px + PAD, bodyY + 44, C_DIM, false);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(rc.getEtichetta()).withColor(rc.getColore()),
                        px + W / 2, bodyY + 52, 0xFFFFFFFF);
            }
            return;
        }

        if (membriEdit && membriSel >= 0 && membriSel < data.getMembri().size()) {
            Membro m = data.getMembri().get(membriSel);

            ctx.fill(px + 4, bodyY - 2, px + W - 4, bodyY + 86, C_FORM_BG);
            border(ctx, px + 4, bodyY - 2, W - 8, 88, C_BORDER, 1);

            ctx.drawText(textRenderer, Text.literal(">> MODIFICA: " + m.getNome().toUpperCase()),
                    px + PAD, bodyY + 2, C_GOLD, true);

            List<RuoloCustom> r = data.getRuoli();
            if (!r.isEmpty()) {
                int idx = Math.min(editRuoloIdx, r.size() - 1);
                RuoloCustom rc = r.get(idx);
                ctx.drawText(textRenderer, Text.literal("Ruolo:"), px + PAD + 20, bodyY + 10, C_DIM, false);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(rc.getEtichetta()).withColor(rc.getColore()), px + W / 2, bodyY + 10, 0xFFFFFFFF);
            }

            int safeStatoIdx = Math.min(editStatoIdx, Membro.Stato.values().length - 1);
            Membro.Stato stato = Membro.Stato.values()[safeStatoIdx];
            ctx.drawText(textRenderer, Text.literal("Stato:"), px + PAD + 20, bodyY + 28, C_DIM, false);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(stato.etichetta).withColor(stato.colore), px + W / 2, bodyY + 28, 0xFFFFFFFF);

            ctx.drawText(textRenderer, Text.literal("Nota:"), px + PAD, bodyY + 44, C_DIM, false);
            return;
        }

        // -- Column headers ---------------------------------------------------
        int hY = bodyY;
        ctx.fill(px + 2, hY, px + W - 2, hY + 12, 0xFF1E1600);
        border(ctx, px + 2, hY, W - 4, 12, 0xFF333333, 1);
        ctx.drawText(textRenderer, Text.literal("MEMBRO"),  px + 8,   hY + 2, C_GOLD_DIM, false);
        ctx.drawText(textRenderer, Text.literal("RUOLO"),   px + 140, hY + 2, C_GOLD_DIM, false);
        ctx.drawText(textRenderer, Text.literal("STATO"),   px + 245, hY + 2, C_GOLD_DIM, false);

        // -- Member list ------------------------------------------------------
        List<Membro> ml = data.getMembri();
        int listY = hY + 13;
        int end   = Math.min(membriScroll + VR, ml.size());
        for (int i = membriScroll; i < end; i++) {
            Membro m   = ml.get(i);
            int    ry  = listY + (i - membriScroll) * RH;
            int    bg  = (i == membriSel) ? C_ROW_SEL : (i % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD);
            ctx.fill(px + 2, ry, px + W - 2, ry + RH - 1, bg);

            if (mx >= px + 2 && mx < px + W - 2 && my >= ry && my < ry + RH - 1)
                ctx.fill(px + 2, ry, px + W - 2, ry + RH - 1, C_ROW_HOVER);

            if (i == membriSel) {
                ctx.fill(px + 2, ry, px + 4, ry + RH - 1, C_GOLD);
            }

            ctx.drawText(textRenderer, Text.literal(m.getNome()),
                    px + 8, ry + 5, C_TEXT, false);
            ctx.drawText(textRenderer,
                    Text.literal(m.getRuolo().getEtichetta()).withColor(m.getRuolo().getColore()),
                    px + 140, ry + 5, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer,
                    Text.literal(m.getStato().etichetta).withColor(m.getStato().colore),
                    px + 245, ry + 5, 0xFFFFFFFF, false);

            if (!m.getNota().isEmpty())
                ctx.drawText(textRenderer, Text.literal("[N]"), px + W - 18, ry + 5, C_GOLD_DIM, false);
        }

        if (membriScroll > 0)
            ctx.drawText(textRenderer, Text.literal("^"), px + W - 12, listY + 2, C_GOLD_DIM, false);
        if (membriScroll + VR < ml.size())
            ctx.drawText(textRenderer, Text.literal("v"), px + W - 12, listY + VR * RH - RH + 2, C_GOLD_DIM, false);

        // -- Stats bar --------------------------------------------------------
        long active = ml.stream().filter(m -> m.getStato() != Membro.Stato.OFFLINE).count();
        ctx.fill(px + 2, py + H - 34, px + W - 2, py + H - 24, C_FORM_BG);
        ctx.drawText(textRenderer,
                Text.literal("Totale: " + ml.size() + "  |  Attivi: " + active),
                px + 8, py + H - 32, C_DIM, false);
    }

    // -- Tab 1 render ---------------------------------------------------------

    private void renderTab1(DrawContext ctx, int mx, int my) {
        int bodyY = py + HEADER_H + TAB_H + 4;

        if (ruoliAdd) {
            ctx.fill(px + 4, bodyY - 2, px + W - 4, bodyY + 60, C_FORM_BG);
            border(ctx, px + 4, bodyY - 2, W - 8, 62, C_BORDER, 1);
            ctx.drawText(textRenderer, Text.literal(">> NUOVO RUOLO"), px + PAD, bodyY + 2, C_GOLD, true);
            ctx.drawText(textRenderer, Text.literal("Nome:"),  px + PAD,  bodyY + 14, C_DIM, false);
            ctx.drawText(textRenderer, Text.literal("Emoji:"), px + PAD,  bodyY + 30, C_DIM, false);
            ctx.drawText(textRenderer, Text.literal("Hex:"),   px + 64,   bodyY + 30, C_DIM, false);
            return;
        }
        if (ruoliEdit && ruoliSel >= 0 && ruoliSel < data.getRuoli().size()) {
            ctx.fill(px + 4, bodyY - 2, px + W - 4, bodyY + 60, C_FORM_BG);
            border(ctx, px + 4, bodyY - 2, W - 8, 62, C_BORDER, 1);
            ctx.drawText(textRenderer, Text.literal(">> MODIFICA RUOLO"), px + PAD, bodyY + 2, C_GOLD, true);
            ctx.drawText(textRenderer, Text.literal("Nome:"),  px + PAD,  bodyY + 14, C_DIM, false);
            ctx.drawText(textRenderer, Text.literal("Emoji:"), px + PAD,  bodyY + 30, C_DIM, false);
            ctx.drawText(textRenderer, Text.literal("Hex:"),   px + 64,   bodyY + 30, C_DIM, false);
            return;
        }

        // -- Column headers ---------------------------------------------------
        int hY = bodyY;
        ctx.fill(px + 2, hY, px + W - 2, hY + 12, 0xFF1E1600);
        border(ctx, px + 2, hY, W - 4, 12, 0xFF333333, 1);
        ctx.drawText(textRenderer, Text.literal("EMOJI"),      px + 8,   hY + 2, C_GOLD_DIM, false);
        ctx.drawText(textRenderer, Text.literal("NOME"),       px + 44,  hY + 2, C_GOLD_DIM, false);
        ctx.drawText(textRenderer, Text.literal("COLORE HEX"), px + 210, hY + 2, C_GOLD_DIM, false);

        // -- Roles list (with scroll) -----------------------------------------
        List<RuoloCustom> rl = data.getRuoli();
        int listY = hY + 13;
        int end = Math.min(ruoliScroll + VR, rl.size());
        for (int i = ruoliScroll; i < end; i++) {
            RuoloCustom r  = rl.get(i);
            int         ry = listY + (i - ruoliScroll) * RH;
            int         bg = (i == ruoliSel) ? C_ROW_SEL : (i % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD);
            ctx.fill(px + 2, ry, px + W - 2, ry + RH - 1, bg);

            if (mx >= px + 2 && mx < px + W - 2 && my >= ry && my < ry + RH - 1)
                ctx.fill(px + 2, ry, px + W - 2, ry + RH - 1, C_ROW_HOVER);

            if (i == ruoliSel) {
                ctx.fill(px + 2, ry, px + 4, ry + RH - 1, C_GOLD);
            }

            ctx.drawText(textRenderer, Text.literal(r.getEmoji()),                          px + 8,   ry + 5, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, Text.literal(r.getNome()).withColor(r.getColore()),   px + 44,  ry + 5, 0xFFFFFFFF, false);

            String hexStr = "#" + String.format("%06X", r.getColore() & 0xFFFFFF);
            ctx.drawText(textRenderer, Text.literal(hexStr), px + 210, ry + 5, r.getColore(), false);

            ctx.fill(px + W - 20, ry + 4, px + W - 8, ry + RH - 5, r.getColore());
            border(ctx, px + W - 20, ry + 4, 12, RH - 9, 0xFF333333, 1);
        }

        if (ruoliScroll > 0)
            ctx.drawText(textRenderer, Text.literal("^"), px + W - 12, listY + 2, C_GOLD_DIM, false);
        if (ruoliScroll + VR < rl.size())
            ctx.drawText(textRenderer, Text.literal("v"), px + W - 12, listY + VR * RH - RH + 2, C_GOLD_DIM, false);

        ctx.fill(px + 2, py + H - 34, px + W - 2, py + H - 24, C_FORM_BG);
        ctx.drawText(textRenderer, Text.literal("Ruoli totali: " + rl.size()),
                px + 8, py + H - 32, C_DIM, false);
    }

    // -- Tab 2 render ---------------------------------------------------------

    private void renderTab2(DrawContext ctx) {
        int bodyY = py + HEADER_H + TAB_H + 4;

        ctx.fill(px + 4, bodyY - 2, px + W - 4, bodyY + 162, C_FORM_BG);
        border(ctx, px + 4, bodyY - 2, W - 8, 164, C_BORDER, 1);

        ctx.drawText(textRenderer, Text.literal(">> Nome famiglia:"), px + PAD, bodyY + 4, C_GOLD, true);
        ctx.drawText(textRenderer, Text.literal(">> Foto profilo:"), px + PAD, bodyY + 38, C_GOLD, true);

        int imgX = px + (W - 64) / 2;
        int imgY = bodyY + 52;
        border(ctx, imgX - 2, imgY - 2, 68, 68, C_BORDER, 1);
        ctx.fill(imgX, imgY, imgX + 64, imgY + 64, 0xFF080808);

        Identifier tex = data.getFotoTexture();
        if (tex != null) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, imgX, imgY, 0, 0, 64, 64, 64, 64);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Nessuna"), imgX + 32, imgY + 24, C_DIM);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("foto"), imgX + 32, imgY + 34, C_DIM);
        }

        String nomeFile = fotoList != null && !fotoList.isEmpty()
                ? fotoList.get(Math.min(fotoIdx, fotoList.size() - 1))
                : (data.getFotoNomeFile().isEmpty() ? "Nessun file" : data.getFotoNomeFile());
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(nomeFile), px + W / 2, imgY + 70, C_DIM);

        ctx.drawText(textRenderer,
                Text.literal("Metti PNG/JPG in: config/famiglia/"),
                px + PAD, bodyY + 148, C_DIM, false);
    }

    // ==========================================================================
    // MOUSE & SCROLL
    // ==========================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (tab == 0 && !membriAdd && !membriEdit) {
            int listY = py + HEADER_H + TAB_H + 4 + 13;
            if (mx >= px + 2 && mx < px + W - 2) {
                List<Membro> ml = data.getMembri();
                for (int i = membriScroll; i < Math.min(membriScroll + VR, ml.size()); i++) {
                    int ry = listY + (i - membriScroll) * RH;
                    if (my >= ry && my < ry + RH - 1) { membriSel = (membriSel == i) ? -1 : i; return true; }
                }
            }
        }
        if (tab == 1 && !ruoliAdd && !ruoliEdit) {
            int listY = py + HEADER_H + TAB_H + 4 + 13;
            if (mx >= px + 2 && mx < px + W - 2) {
                List<RuoloCustom> rl = data.getRuoli();
                for (int i = ruoliScroll; i < Math.min(ruoliScroll + VR, rl.size()); i++) {
                    int ry = listY + (i - ruoliScroll) * RH;
                    if (my >= ry && my < ry + RH - 1) { ruoliSel = (ruoliSel == i) ? -1 : i; return true; }
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (tab == 0 && !membriAdd && !membriEdit) {
            membriScroll -= (int) Math.signum(vAmt);
            membriScroll  = Math.max(0, Math.min(membriScroll, Math.max(0, data.getMembri().size() - VR)));
            return true;
        }
        if (tab == 1 && !ruoliAdd && !ruoliEdit) {
            ruoliScroll -= (int) Math.signum(vAmt);
            ruoliScroll  = Math.max(0, Math.min(ruoliScroll, Math.max(0, data.getRuoli().size() - VR)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (membriAdd || membriEdit || ruoliAdd || ruoliEdit) {
                membriAdd = membriEdit = ruoliAdd = ruoliEdit = false;
                clearChildren(); init(); return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean shouldPause() { return false; }

    // ==========================================================================
    // HELPERS
    // ==========================================================================

    private void border(DrawContext ctx, int x, int y, int w, int h, int col, int t) {
        ctx.fill(x,       y,       x + w,   y + t,   col);
        ctx.fill(x,       y + h - t, x + w, y + h,   col);
        ctx.fill(x,       y,       x + t,   y + h,   col);
        ctx.fill(x + w - t, y,     x + w,   y + h,   col);
    }

    private void cycleButton(int x, int y, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> { action.run(); clearChildren(); init(); })
                .dimensions(x, y, 16, 14).build());
    }

    private void confirmCancel(int btnY, Runnable confirm, Runnable cancel) {
        addDrawableChild(ButtonWidget.builder(Text.literal("Conferma"), btn -> confirm.run())
                .dimensions(px + PAD, btnY, 96, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Annulla"), btn -> cancel.run())
                .dimensions(px + W - PAD - 96, btnY, 96, 16).build());
    }
}
