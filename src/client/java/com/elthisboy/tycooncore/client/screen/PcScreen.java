package com.elthisboy.tycooncore.client.screen;

import com.elthisboy.tycooncore.client.ClientPlayerDataCache;
import com.elthisboy.tycooncore.client.ClientSabotageCache;
import com.elthisboy.tycooncore.client.ClientUpgradeCache;
import com.elthisboy.tycooncore.gym.GymSessionManager;
import com.elthisboy.tycooncore.network.packet.RequestSyncPayload;
import com.elthisboy.tycooncore.network.packet.SabotageActionPayload;
import com.elthisboy.tycooncore.network.packet.UpgradeClientEntry;
import com.elthisboy.tycooncore.network.packet.UpgradeRequestPayload;
import com.elthisboy.tycooncore.screen.PcScreenHandler;
import com.elthisboy.tycooncore.tier.TierManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * High-end Gym Management PC screen.
 *
 * UX guarantees:
 *   - No text ever overflows its container (all paths clipped or dynamically sized)
 *   - Buttons auto-expand to fit translated labels
 *   - Visual scrollbar with drag support when list exceeds viewport
 *   - Final tier shows "MAX TIER" instead of progress fraction
 *   - Three-column footer never overlaps regardless of text length
 *   - Tier-based TierTheme evolves the entire palette and effects
 */
public class PcScreen extends HandledScreen<PcScreenHandler> {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int GUI_W    = 282;
    private static final int GUI_H    = 226;
    private static final int HEADER_H = 24;
    private static final int FOOTER_H = 28;
    private static final int TAB_Y    = HEADER_H;
    private static final int TAB_H    = 18;
    private static final int TAB_GAP  = 2;
    private static final int LIST_PAD = 4;
    private static final int CARD_H   = 46;
    private static final int CARD_GAP = 3;
    private static final int CARD_MX  = 5;    // horizontal margin for cards
    private static final int STRIPE_W = 3;    // left accent stripe
    private static final int ICON_W   = 16;   // category icon column

    // Button sizing: grows with text, has a minimum
    private static final int BTN_W_MIN = 36;
    private static final int BTN_PAD   = 5;   // horizontal inner padding
    private static final int BTN_H     = 14;
    private static final int BTN_MR    = 5;   // button right margin from card edge

    // Scrollbar
    private static final int SB_W   = 5;  // scrollbar width
    private static final int SB_PAD = 2;  // gap between cards and scrollbar

    // Footer column widths (thirds of GUI_W)
    private static final int COL_W = GUI_W / 3;

    // ── Tier themes ───────────────────────────────────────────────────────────
    private static final TierTheme[] THEMES = {
        // Tier 1 – Basic / grey
        new TierTheme(
            0xFF1A1A1A, 0xFF2D2D2D, 0xFF1A1A1A,
            0xFF666666, 0xFF333333,
            0xFF222222, 0xFF2A2A2A, 0xFF888888,
            0xFF212121, 0xFF2A2A2A, 0xFF333333, 0xFF555555,
            0xFF555555, 0xFF888888, 0xFF2A2A2A,
            0xFF334433, 0xFF3A5C3A,
            false, false
        ),
        // Tier 2 – Blue / professional
        new TierTheme(
            0xFF0D1117, 0xFF21262D, 0xFF161B22,
            0xFF1F6FEB, 0xFF30363D,
            0xFF161B22, 0xFF1C2128, 0xFF58A6FF,
            0xFF161B22, 0xFF1C2128, 0xFF30363D, 0xFF1F6FEB,
            0xFF1F6FEB, 0xFF21A366, 0xFF374151,
            0xFF238636, 0xFF2EA043,
            false, false
        ),
        // Tier 3 – Purple / advanced  (glow)
        new TierTheme(
            0xFF0D0B1A, 0xFF1D1535, 0xFF0D0B1A,
            0xFF7C3AED, 0xFF3D2B6B,
            0xFF130F22, 0xFF1C1635, 0xFFA78BFA,
            0xFF130F22, 0xFF1C1635, 0xFF3D2B6B, 0xFF7C3AED,
            0xFF7C3AED, 0xFF059669, 0xFF374151,
            0xFF238636, 0xFF2EA043,
            true, false
        ),
        // Tier 4+ – Gold / premium  (shimmer + pulse)
        new TierTheme(
            0xFF0A0800, 0xFF1A1200, 0xFF0A0800,
            0xFFD97706, 0xFF78350F,
            0xFF120E00, 0xFF1A1500, 0xFFFFD700,
            0xFF120E00, 0xFF1C1500, 0xFF78350F, 0xFFD97706,
            0xFFF59E0B, 0xFF059669, 0xFF374151,
            0xFF92400E, 0xFFB45309,
            true, true
        ),
    };

    // ── Animation state ───────────────────────────────────────────────────────
    private long   openMs      = -1;
    private String flashId     = null;
    private long   flashMs     = 0;
    private long   tierUpMs    = 0;
    private long   tabSwitchMs = 0;

    // ── Screen state ──────────────────────────────────────────────────────────
    private String  currentCategory = "";
    private int     scrollOffset    = 0;

    // ── Tooltip hover state ───────────────────────────────────────────────────
    private UpgradeClientEntry hoveredEntry = null;
    private int                hoveredLevel = 0;

    // ── PC on/off state (session-only: always starts OFF when screen opens) ──
    private boolean pcOn = false;

    // ── OFF-screen boot button bounds (computed in drawOffScreen) ─────────────
    private int offBtnX, offBtnY, offBtnW, offBtnH;

    // ── Header power button bounds (computed in drawHeader, only when ON) ─────
    private int gymBtnX, gymBtnY, gymBtnW, gymBtnH;

    // ── Scrollbar drag state ──────────────────────────────────────────────────
    private boolean isDraggingSb = false;
    private int     sbDragTotalCards = 0;
    private int     sbDragVisible    = 0;
    private int     sbListTop        = 0;
    private int     sbListBottom     = 0;

    // ── Constructor ───────────────────────────────────────────────────────────
    public PcScreen(PcScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth  = GUI_W;
        this.backgroundHeight = GUI_H;
    }

    @Override
    protected void init() {
        super.init();
        openMs = -1;
        pcOn   = false;          // PC always boots in OFF state
        List<String> cats = ClientUpgradeCache.getCategories();
        if (currentCategory.isEmpty() || !cats.contains(currentCategory)) {
            currentCategory = cats.isEmpty() ? "" : cats.get(0);
        }
        scrollOffset = 0;
    }

    // ── Top-level render ──────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (openMs < 0) openMs = System.currentTimeMillis();
        hoveredEntry = null;   // reset every frame; drawCardList repopulates
        super.render(ctx, mx, my, delta);

        // When the PC is off, only the boot screen is shown — no overlays/tooltips.
        if (!pcOn) return;

        // ── Sabotage overlay: drawn AFTER super.render() so it sits on top of
        // all card/tab/foreground elements rendered by HandledScreen. ──────────
        if (ClientSabotageCache.active) {
            int ox = (width  - GUI_W) / 2;
            int oy = (height - GUI_H) / 2;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 300);
            drawSabotageOverlay(ctx, ox, oy, mx, my);
            ctx.getMatrices().pop();
            // Discard any card-hover state so the tooltip never appears over the overlay
            hoveredEntry = null;
        }

        // Tooltip: rendered last, at Z+400 so it sits above all UI elements.
        // Only drawn when there is no active sabotage overlay.
        drawMouseoverTooltip(ctx, mx, my);
        if (hoveredEntry != null) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 400);
            drawUpgradeTooltip(ctx, mx, my, hoveredEntry, hoveredLevel);
            ctx.getMatrices().pop();
        }
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        long      now = System.currentTimeMillis();
        int        ox = (width  - GUI_W) / 2;
        int        oy = (height - GUI_H) / 2;
        TierTheme  th = theme();

        // Always draw the outer frame (panel shape stays the same ON or OFF)
        drawFrame(ctx, ox, oy, th, now);

        if (!pcOn) {
            // ── PC is OFF: show a boot screen, nothing else ───────────────────
            drawOffScreen(ctx, ox, oy, mx, my, th);
            return;
        }

        // ── PC is ON: full normal UI ──────────────────────────────────────────
        drawHeader(ctx, ox, oy, mx, my, th, now);
        drawFooter(ctx, ox, oy, th);

        if (ClientUpgradeCache.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("pc.loading"),
                ox + GUI_W / 2, oy + GUI_H / 2 - 4, 0x888888);
        } else {
            drawTabs(ctx, ox, oy, mx, my, th, now);
            int cTop = oy + TAB_Y + TAB_H + 1;
            int cBot = oy + GUI_H - FOOTER_H;
            ctx.enableScissor(ox, cTop, ox + GUI_W, cBot);
            drawCardList(ctx, ox, oy, mx, my, th, now);
            ctx.disableScissor();
        }

        drawTierUpEffect(ctx, ox, oy, th, now);
        drawOpenFade(ctx, ox, oy, now);
    }

    // ── Frame ─────────────────────────────────────────────────────────────────

    private void drawFrame(DrawContext ctx, int ox, int oy, TierTheme th, long now) {
        ctx.fill(ox - 2, oy + 3, ox + GUI_W + 2, oy + GUI_H + 3, 0x77000000);
        ctx.fill(ox - 1, oy - 1, ox + GUI_W + 1, oy + GUI_H + 1, th.panelBorder);
        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, th.bg);
        if (th.hasGlow) drawOuterGlow(ctx, ox, oy, th.accentLine, 5);
        if (th.hasPulse) {
            float phase = (now % 4000) / 4000f;
            int   a     = (int)(35 * (0.5f + 0.5f * Math.sin(2 * Math.PI * phase)));
            int   rgb   = th.accentLine & 0x00FFFFFF;
            ctx.fill(ox - 2, oy - 2, ox + GUI_W + 2, oy - 1,               (a << 24) | rgb);
            ctx.fill(ox - 2, oy + GUI_H + 1, ox + GUI_W + 2, oy + GUI_H + 2, (a << 24) | rgb);
            ctx.fill(ox - 2, oy - 2, ox - 1,             oy + GUI_H + 2,   (a << 24) | rgb);
            ctx.fill(ox + GUI_W + 1, oy - 2, ox + GUI_W + 2, oy + GUI_H + 2, (a << 24) | rgb);
        }
    }

    // ── PC OFF boot screen ────────────────────────────────────────────────────

    /**
     * Drawn when pcOn == false.  Shows a dark idle screen with a centred
     * power button.  Clicking the button sets pcOn = true.
     */
    private void drawOffScreen(DrawContext ctx, int ox, int oy, int mx, int my,
                               TierTheme th) {
        // Dark overlay over the whole inner panel
        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, 0xF0050505);

        int cx = ox + GUI_W / 2;
        int cy = oy + GUI_H / 2;

        // "SYSTEM OFFLINE" label
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("pc.boot.offline"),
            cx, cy - 28, 0xFFFFFF);

        // Large power / start button
        int btnW = 64, btnH = 18;
        offBtnX = cx - btnW / 2;
        offBtnY = cy - btnH / 2;
        offBtnW = btnW;
        offBtnH = btnH;

        boolean hov = mx >= offBtnX && mx < offBtnX + offBtnW
                   && my >= offBtnY && my < offBtnY + offBtnH;

        int bg     = hov ? 0xFF0D4A1A : 0xFF081A0A;
        int border = hov ? 0xFF4ADE80 : 0xFF2EA043;

        ctx.fill(offBtnX, offBtnY, offBtnX + offBtnW, offBtnY + offBtnH, bg);
        ctx.fill(offBtnX, offBtnY,             offBtnX + offBtnW, offBtnY + 1,       border);
        ctx.fill(offBtnX, offBtnY + offBtnH - 1, offBtnX + offBtnW, offBtnY + offBtnH, border);
        ctx.fill(offBtnX, offBtnY,             offBtnX + 1,       offBtnY + offBtnH, border);
        ctx.fill(offBtnX + offBtnW - 1, offBtnY, offBtnX + offBtnW, offBtnY + offBtnH, border);

        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable(hov ? "pc.boot.power_on_hover" : "pc.boot.power_on"),
            cx, offBtnY + (offBtnH - textRenderer.fontHeight) / 2,
            0xFFFFFF);

        // Subtle blinking cursor hint below the button
        long now = System.currentTimeMillis();
        if ((now / 700) % 2 == 0) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("pc.boot.hint"),
                cx, offBtnY + offBtnH + 8, 0xFFFFFF);
        }
    }

    private void drawOuterGlow(DrawContext ctx, int ox, int oy, int col, int layers) {
        int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
        for (int i = layers; i >= 1; i--) {
            int a = (int)(40 * i / (float) layers);
            int c = (a << 24) | (r << 16) | (g << 8) | b;
            ctx.fill(ox - i, oy - i,               ox + GUI_W + i, oy - i + 1,         c);
            ctx.fill(ox - i, oy + GUI_H + i - 1,   ox + GUI_W + i, oy + GUI_H + i,     c);
            ctx.fill(ox - i, oy - i,               ox - i + 1,     oy + GUI_H + i,     c);
            ctx.fill(ox + GUI_W + i - 1, oy - i,  ox + GUI_W + i, oy + GUI_H + i,     c);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(DrawContext ctx, int ox, int oy, int mx, int my,
                            TierTheme th, long now) {
        ctx.fillGradient(ox, oy, ox + GUI_W, oy + HEADER_H, th.headerTop, th.headerBot);
        ctx.fill(ox, oy + HEADER_H - 1, ox + GUI_W, oy + HEADER_H, th.accentLine);
        if (th.hasPulse) {
            float phase = (now % 2500) / 2500f;
            int   shimX = (int)(ox + GUI_W * phase);
            int   a     = (int)(50 * Math.sin(Math.PI * phase));
            if (a > 0) ctx.fill(shimX - 30, oy, shimX + 30, oy + HEADER_H - 1, (a << 24) | 0xFFFFFF);
        }

        // ── Power button (top-right corner of header) ─────────────────────────
        // Bounds stored so mouseClicked can hit-test them.
        gymBtnW = 16;
        gymBtnH = 14;
        gymBtnX = ox + GUI_W - gymBtnW - 4;
        gymBtnY = oy + (HEADER_H - gymBtnH) / 2;

        // PC is ON when drawHeader is called — button turns it OFF on click
        boolean hovPwr = mx >= gymBtnX && mx < gymBtnX + gymBtnW
                      && my >= gymBtnY && my < gymBtnY + gymBtnH;

        // Green = on; turns red on hover to hint at shutting down
        int pBg     = hovPwr ? 0xFF4A0A0A : 0xFF0D4A1A;
        int pBorder = hovPwr ? 0xFFFF4444 : 0xFF2EA043;
        ctx.fill(gymBtnX,             gymBtnY,              gymBtnX + gymBtnW, gymBtnY + gymBtnH, pBg);
        // Border (4 edges)
        ctx.fill(gymBtnX,             gymBtnY,              gymBtnX + gymBtnW, gymBtnY + 1,       pBorder);
        ctx.fill(gymBtnX,             gymBtnY + gymBtnH - 1, gymBtnX + gymBtnW, gymBtnY + gymBtnH, pBorder);
        ctx.fill(gymBtnX,             gymBtnY,              gymBtnX + 1,       gymBtnY + gymBtnH, pBorder);
        ctx.fill(gymBtnX + gymBtnW - 1, gymBtnY,            gymBtnX + gymBtnW, gymBtnY + gymBtnH, pBorder);
        // Power icon: always green while PC is on; dims on hover (about to turn off)
        String pwrIcon = hovPwr ? "§c●" : "§a●";
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(pwrIcon),
            gymBtnX + gymBtnW / 2,
            gymBtnY + (gymBtnH - textRenderer.fontHeight) / 2,
            0xFFFFFF);

        // ── Title (centred, clipped to avoid overlapping the power button) ────
        String star  = tier() >= 4 ? "§6★ " : tier() >= 3 ? "§d✦ " : tier() >= 2 ? "§9✦ " : "§7• ";
        String tag   = "§8[T" + tier() + "]§r ";
        String title = Text.translatable("pc.title").getString();
        String full  = star + tag + title;
        // Leave room for the power button on the right
        int maxW = GUI_W - gymBtnW - 12;
        if (textRenderer.getWidth(full.replaceAll("§.", "")) > maxW)
            full = textRenderer.trimToWidth(full, maxW);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(full),
            ox + (GUI_W - gymBtnW) / 2,   // shift centre left to account for button
            oy + (HEADER_H - textRenderer.fontHeight) / 2,
            0xFFFFFF);
    }

    // ── Footer / dashboard ────────────────────────────────────────────────────

    private void drawFooter(DrawContext ctx, int ox, int oy, TierTheme th) {
        int fy    = oy + GUI_H - FOOTER_H;
        int textY = fy + 4;
        int barY  = textY + textRenderer.fontHeight + 3;
        int barW  = GUI_W - 12;

        ctx.fill(ox, fy, ox + GUI_W, fy + 1, th.accentLine);
        ctx.fillGradient(ox, fy + 1, ox + GUI_W, oy + GUI_H, th.headerBot, th.headerTop);

        int  tier   = tier();
        int  prog   = ClientPlayerDataCache.upgradeProgress;
        int  req    = TierManager.getRequiredUpgrades(tier);
        long money  = ClientPlayerDataCache.money;
        String sym  = ClientUpgradeCache.getCurrencySymbol();
        boolean maxTier = tier >= TierManager.MAX_TIER && prog >= req;

        // ── Left column: money (clipped to COL_W - 4) ─────────────────────────
        String moneyRaw = sym + formatMoney(money);
        String moneyIcon = "§e● §6";
        int    moneyMaxW = COL_W - 4;
        if (textRenderer.getWidth(moneyRaw) > moneyMaxW - textRenderer.getWidth("● ")) {
            int ew = textRenderer.getWidth("…");
            moneyRaw = textRenderer.trimToWidth(moneyRaw, moneyMaxW - textRenderer.getWidth("● ") - ew) + "…";
        }
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(moneyIcon + moneyRaw), ox + 6, textY, 0xFFD700);

        // ── Centre column: tier + live income (plain display, no button) ───────
        int     income  = ClientPlayerDataCache.incomePerSecond;
        boolean gymOpen = ClientPlayerDataCache.gymActive;
        String centerFull = "§9T" + tier + " §8| "
            + (gymOpen ? "§a+" : "§7+") + sym
            + GymSessionManager.formatMoney(income) + "§7/s";
        // Clip to column width if necessary
        if (textRenderer.getWidth(centerFull.replaceAll("§.", "")) > COL_W - 2)
            centerFull = (gymOpen ? "§a+" : "§7+") + sym
                + GymSessionManager.formatMoney(income) + "§7/s";
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(centerFull), ox + GUI_W / 2, textY,
            gymOpen ? 0x3FB950 : 0x888888);

        // ── Right column: progress OR max tier OR sabotage blocked ───────────
        String rightStr;
        int    rightCol;
        if (ClientSabotageCache.active) {
            rightStr = Text.translatable("pc.footer.blocked").getString();
            rightCol = 0xFF4444;
        } else if (maxTier) {
            rightStr = Text.translatable("pc.footer.max_tier").getString();
            rightCol = 0xFFD700;
        } else {
            String progStr = Text.translatable("pc.footer.progress", prog, req).getString();
            rightStr = "§a▲ §2" + progStr;
            rightCol = 0x3FB950;
        }
        // Right-align within right column, clip if needed
        String rightPlain = rightStr.replaceAll("§.", "");
        int    rightW     = textRenderer.getWidth(rightPlain);
        int    rightMaxW  = COL_W - 4;
        if (rightW > rightMaxW) {
            rightPlain = textRenderer.trimToWidth(rightPlain, rightMaxW - textRenderer.getWidth("…")) + "…";
            rightStr   = rightPlain;
        }
        rightW = textRenderer.getWidth(rightStr.replaceAll("§.", ""));
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(rightStr), ox + GUI_W - rightW - 6, textY, rightCol);

        // ── Progress bar ──────────────────────────────────────────────────────
        int barX = ox + 6;
        ctx.fill(barX, barY, barX + barW, barY + 4, 0xFF1A2A1A);
        if (maxTier) {
            // Full bar in gold for max tier
            ctx.fill(barX, barY, barX + barW, barY + 4, 0xFF78350F);
            ctx.fill(barX, barY, barX + barW, barY + 4,
                0xFF000000 | (0xFFD700 & 0x00FFFFFF & 0x88FFFFFF));
            // Simpler: gold fill
            ctx.fill(barX, barY, barX + barW, barY + 4, 0xFF6B4C0A);
            ctx.fill(barX, barY, barX + barW / 2, barY + 2, 0x44FFD700);
        } else if (req > 0) {
            int filled = (int)(barW * Math.min((float) prog / req, 1f));
            if (filled > 0) {
                ctx.fill(barX, barY, barX + filled, barY + 4,
                    0xFF000000 | (th.stripeAvail & 0x00FFFFFF));
            }
        }
        ctx.fill(barX, barY, barX + barW, barY + 1, 0x22FFFFFF);
    }

    // ── Tab bar ───────────────────────────────────────────────────────────────

    private void drawTabs(DrawContext ctx, int ox, int oy, int mx, int my,
                          TierTheme th, long now) {
        List<String> cats = ClientUpgradeCache.getCategories();
        if (cats.isEmpty()) return;
        int tabW = evenTabWidth(cats.size());
        int tx = ox, ty = oy + TAB_Y;
        ctx.fill(ox, ty, ox + GUI_W, ty + TAB_H, th.bg);

        for (String cat : cats) {
            boolean active = cat.equalsIgnoreCase(currentCategory);
            boolean hov    = !active && mx >= tx && mx < tx + tabW && my >= ty && my < ty + TAB_H;
            int     bg     = active ? th.bg : hov ? th.tabHov : th.tab;
            ctx.fill(tx, ty, tx + tabW, ty + TAB_H, bg);
            ctx.fill(tx + tabW - 1, ty, tx + tabW, ty + TAB_H, th.panelBorder);

            if (active) {
                ctx.fill(tx, ty + TAB_H - 2, tx + tabW - 1, ty + TAB_H, th.tabUnderline);
                long sw = now - tabSwitchMs;
                if (sw < 250) {
                    int a = (int)(70 * (1f - sw / 250f));
                    ctx.fill(tx, ty, tx + tabW - 1, ty + TAB_H - 2, (a << 24) | 0xFFFFFF);
                }
                if (th.hasGlow) ctx.fill(tx, ty, tx + tabW - 1, ty + 1, th.tabUnderline);
            } else if (hov) {
                ctx.fill(tx, ty + TAB_H - 1, tx + tabW - 1, ty + TAB_H, th.panelBorder);
            }

            // Icon + label, centred, clipped to tab interior
            String icon  = categoryIcon(cat);
            String label = Text.translatable(categoryKey(cat)).getString();
            int    areaW = tabW - 6;
            String full  = icon + " " + label;
            if (textRenderer.getWidth(full) > areaW) {
                full = textRenderer.getWidth(label) <= areaW ? label : icon;
            }
            // Final safety clip
            if (textRenderer.getWidth(full) > areaW) {
                full = textRenderer.trimToWidth(full, areaW - textRenderer.getWidth("…")) + "…";
            }
            int col = active ? 0xFFFFFF : hov ? 0xCCCCCC : 0x888888;
            ctx.drawTextWithShadow(textRenderer, Text.literal(full),
                tx + (tabW - textRenderer.getWidth(full)) / 2,
                ty + (TAB_H - textRenderer.fontHeight) / 2,
                col);
            tx += tabW + TAB_GAP;
        }
        ctx.fill(ox, oy + TAB_Y + TAB_H, ox + GUI_W, oy + TAB_Y + TAB_H + 1, th.panelBorder);
    }

    // ── Card list ─────────────────────────────────────────────────────────────

    private void drawCardList(DrawContext ctx, int ox, int oy, int mx, int my,
                              TierTheme th, long now) {
        if (currentCategory.isEmpty()) return;
        List<UpgradeClientEntry> upgrades = ClientUpgradeCache.getByCategory(currentCategory);
        int listTop    = oy + TAB_Y + TAB_H + 1 + LIST_PAD;
        int listBottom = oy + GUI_H - FOOTER_H - LIST_PAD;
        int visible    = visibleCards(listTop, listBottom);
        boolean needsScroll = upgrades.size() > visible;

        // Reserve right side for scrollbar when needed
        int sbX    = ox + GUI_W - CARD_MX;          // right edge of card area
        int cardW  = GUI_W - CARD_MX * 2 - (needsScroll ? SB_W + SB_PAD : 0);
        int rx     = ox + CARD_MX;
        int ry     = listTop;

        // Cache for drag state
        sbListTop    = listTop;
        sbListBottom = listBottom;
        sbDragTotalCards = upgrades.size();
        sbDragVisible    = visible;

        for (int i = scrollOffset; i < upgrades.size(); i++) {
            if (ry + CARD_H > listBottom) break;
            UpgradeClientEntry e    = upgrades.get(i);
            int     lvl    = ClientPlayerDataCache.getUpgradeLevel(e.id());
            boolean max    = lvl >= e.maxLevel();
            boolean locked = ClientPlayerDataCache.tierLevel < e.requiredTier();
            long    cost   = e.getCostForLevel(lvl);
            boolean afford = ClientPlayerDataCache.money >= cost;
            boolean hov    = mx >= rx && mx < rx + cardW && my >= ry && my < ry + CARD_H;
            if (hov) { hoveredEntry = e; hoveredLevel = lvl; }
            drawCard(ctx, e, lvl, max, locked, afford, cost, rx, ry, cardW, hov, th, now);
            ry += CARD_H + CARD_GAP;
        }

        if (needsScroll) {
            drawScrollbar(ctx, ox + GUI_W - CARD_MX - SB_W, listTop, listBottom,
                upgrades.size(), visible, th, now);
        }
    }

    private void drawScrollbar(DrawContext ctx, int sbX, int listTop, int listBottom,
                               int total, int visible, TierTheme th, long now) {
        int trackH = listBottom - listTop;
        // Track background
        ctx.fill(sbX, listTop, sbX + SB_W, listBottom, blendDark(th.cardBg, 0.6f));
        ctx.fill(sbX, listTop, sbX + 1, listBottom, th.panelBorder);

        if (total <= visible || trackH <= 0) return;

        int thumbH  = Math.max(14, trackH * visible / total);
        int maxScr  = total - visible;
        int maxOff  = trackH - thumbH;
        int thumbY  = listTop + (maxScr > 0 ? (int)((float) scrollOffset / maxScr * maxOff) : 0);

        // Thumb
        int thumbCol = isDraggingSb ? lightenRgb(th.stripeAvail, 40) : th.stripeAvail;
        ctx.fill(sbX + 1, thumbY,     sbX + SB_W, thumbY + thumbH, thumbCol);
        ctx.fill(sbX + 1, thumbY,     sbX + SB_W, thumbY + 1,      lightenRgb(thumbCol, 60));
        ctx.fill(sbX + 1, thumbY + thumbH - 1, sbX + SB_W, thumbY + thumbH,
            blendDark(thumbCol, 0.6f));
    }

    // ── Single card ───────────────────────────────────────────────────────────

    private void drawCard(DrawContext ctx, UpgradeClientEntry e,
                          int lvl, boolean max, boolean locked, boolean afford,
                          long cost, int rx, int ry, int cardW, boolean hov,
                          TierTheme th, long now) {
        boolean flashing = e.id().equals(flashId) && now - flashMs < 500;
        String sym    = ClientUpgradeCache.getCurrencySymbol();
        String lbl    = btnLabel(e, lvl, max, locked);
        int    bW     = dynBtnW(lbl);         // dynamic button width
        int    btnX   = rx + cardW - bW - BTN_MR;
        int    btnY   = ry + (CARD_H - BTN_H) / 2;
        int    textX  = rx + STRIPE_W + ICON_W + 5;
        int    textW  = cardW - STRIPE_W - ICON_W - bW - BTN_MR - 12;

        // ── Background ────────────────────────────────────────────────────────
        int cardBg = locked ? blendDark(th.cardBg, 0.7f)
                   : max    ? blendColor(th.cardBg, th.stripeMaxed, 0.06f)
                   : hov    ? th.cardHov
                            : th.cardBg;
        ctx.fill(rx, ry, rx + cardW, ry + CARD_H, cardBg);

        // ── Borders ───────────────────────────────────────────────────────────
        int topB = hov && !locked ? th.cardHovBorder : th.cardBorder;
        ctx.fill(rx, ry,              rx + cardW, ry + 1,      topB);
        ctx.fill(rx, ry + CARD_H - 1, rx + cardW, ry + CARD_H, th.cardBorder);
        ctx.fill(rx + cardW - 1, ry,  rx + cardW, ry + CARD_H, th.cardBorder);

        if (th.hasGlow && hov && !locked) {
            int gr = (th.stripeAvail >> 16) & 0xFF;
            int gg = (th.stripeAvail >>  8) & 0xFF;
            int gb =  th.stripeAvail        & 0xFF;
            for (int i = 1; i <= 5; i++) {
                int a = (int)(25 * (6 - i) / 5f);
                ctx.fill(rx + cardW - i, ry, rx + cardW, ry + CARD_H,
                    (a << 24) | (gr << 16) | (gg << 8) | gb);
            }
        }

        // ── Stripe ────────────────────────────────────────────────────────────
        int stripe = locked ? th.stripeLocked : max ? th.stripeMaxed : th.stripeAvail;
        ctx.fill(rx, ry, rx + STRIPE_W, ry + CARD_H, stripe);

        // ── Icon ──────────────────────────────────────────────────────────────
        String icon  = categoryIcon(currentCategory);
        int    iconX = rx + STRIPE_W + (ICON_W - textRenderer.getWidth(icon)) / 2;
        int    iconY = ry + (CARD_H - textRenderer.fontHeight) / 2;
        ctx.drawTextWithShadow(textRenderer, Text.literal(icon), iconX, iconY,
            locked ? 0x444444 : lightenRgb(stripe, 90));

        // ── Flash ─────────────────────────────────────────────────────────────
        if (flashing) {
            float t = (now - flashMs) / 500f;
            int   a = (int)(150 * (1f - t));
            ctx.fill(rx, ry, rx + cardW, ry + CARD_H, (a << 24) | 0x00FF88);
        }

        // ── Upgrade name (clamped to textW) ───────────────────────────────────
        String pfx = locked ? "§8" : max ? "§7" : "§f";
        drawClipped(ctx,
            Text.literal(pfx).append(Text.translatable(e.getNameKey())),
            textX, ry + 7, Math.max(1, textW), 0xFFFFFF);

        // ── Badge ─────────────────────────────────────────────────────────────
        int badgeY = ry + 7 + textRenderer.fontHeight + 3;
        drawBadge(ctx, e, lvl, max, locked, textX, badgeY, textW);

        // ── Description (one line, clipped) ───────────────────────────────────
        List<OrderedText> desc = textRenderer.wrapLines(
            Text.literal(Text.translatable(e.getDescKey()).getString()),
            Math.max(1, textW));
        int descY = badgeY + textRenderer.fontHeight + 4;
        if (!desc.isEmpty() && descY + textRenderer.fontHeight < ry + CARD_H) {
            ctx.drawTextWithShadow(textRenderer, desc.get(0), textX, descY, 0x44465A);
        }

        // ── Button ────────────────────────────────────────────────────────────
        drawButton(ctx, lbl, lvl, max, locked, afford, sym, cost,
            btnX, btnY, bW, hov, th);
    }

    private void drawBadge(DrawContext ctx, UpgradeClientEntry e,
                           int lvl, boolean max, boolean locked,
                           int x, int y, int maxW) {
        String label;
        int    bg;
        if (max) {
            label = Text.translatable("pc.label.max").getString();
            bg    = 0xFF374151;
        } else if (locked) {
            label = "T" + e.requiredTier() + " REQ";
            bg    = 0xFF7F1D1D;
        } else {
            label = Text.translatable("pc.label.level", lvl, e.maxLevel()).getString();
            bg    = 0xFF1E3A5F;
        }
        // Clip badge text to available width
        int halfW = (maxW - 4) / 2;
        if (textRenderer.getWidth(label) > halfW) {
            label = textRenderer.trimToWidth(label, halfW - textRenderer.getWidth("…")) + "…";
        }
        int lw = textRenderer.getWidth(label) + 6;
        ctx.fill(x - 2, y - 1, x + lw - 2, y + textRenderer.fontHeight + 1, bg);
        ctx.drawTextWithShadow(textRenderer, Text.literal("§f" + label), x, y, 0xFFFFFF);

        // Income bonus badge — shown to the right of the level badge
        if (e.incomeBonus() > 0) {
            String incStr = "§a+" + e.incomeBonus() + "/s";
            int    incX   = x + lw + 2;
            int    incW   = textRenderer.getWidth(incStr.replaceAll("§.", ""));
            if (incX + incW <= x + maxW) {
                ctx.fill(incX - 2, y - 1, incX + incW + 2, y + textRenderer.fontHeight + 1, 0xFF0A2A0A);
                ctx.drawTextWithShadow(textRenderer, Text.literal(incStr), incX, y, 0x3FB950);
            }
        }
    }

    private void drawButton(DrawContext ctx, String label,
                            int lvl, boolean max, boolean locked, boolean afford,
                            String sym, long cost,
                            int btnX, int btnY, int bW, boolean cardHov, TierTheme th) {
        int btnBg = locked ? 0xFF21262D
                  : max    ? 0xFF2D333B
                  : cardHov && afford ? th.btnBuyHov
                  : afford            ? th.btnBuy
                                      : 0xFF6E1515;

        ctx.fill(btnX + 1, btnY + 1, btnX + bW + 1, btnY + BTN_H + 1, 0x66000000);
        ctx.fill(btnX,     btnY,     btnX + bW,     btnY + BTN_H,     btnBg);
        ctx.fill(btnX,     btnY,     btnX + bW,     btnY + 1,         0x22FFFFFF);

        // Label is guaranteed to fit because bW = max(BTN_W_MIN, textW + 2*BTN_PAD)
        int lw = textRenderer.getWidth(label);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label),
            btnX + (bW - lw) / 2,
            btnY + (BTN_H - textRenderer.fontHeight) / 2,
            0xFFFFFF);

        if (!max && !locked) {
            String costStr = sym + formatMoney(cost);
            // Clip cost to button width
            if (textRenderer.getWidth(costStr) > bW) {
                costStr = textRenderer.trimToWidth(costStr, bW - textRenderer.getWidth("…")) + "…";
            }
            int cw = textRenderer.getWidth(costStr);
            ctx.drawTextWithShadow(textRenderer, Text.literal(costStr),
                btnX + (bW - cw) / 2,
                btnY + BTN_H + 3,
                afford ? 0xFFD700 : 0xFF4444);
        }
    }

    // ── Upgrade tooltip ───────────────────────────────────────────────────────

    private void drawUpgradeTooltip(DrawContext ctx, int mx, int my,
                                    UpgradeClientEntry e, int lvl) {
        int    tier     = ClientPlayerDataCache.tierLevel;
        boolean max     = lvl >= e.maxLevel();
        boolean locked  = tier < e.requiredTier();
        long   cost     = e.getCostForLevel(lvl);
        boolean afford  = ClientPlayerDataCache.money >= cost;
        String sym      = ClientUpgradeCache.getCurrencySymbol();

        // ── Build lines ───────────────────────────────────────────────────────
        int     maxLineW = 180;   // max tooltip content width in pixels

        // 1. Name (bold-style: bright white)
        String nameRaw = Text.translatable(e.getNameKey()).getString();
        String namePfx = max ? "§7" : locked ? "§8" : "§f";

        // 2. Full description – wrap to maxLineW
        String descRaw = Text.translatable(e.getDescKey()).getString();
        List<OrderedText> descWrapped = textRenderer.wrapLines(
            Text.literal(descRaw), maxLineW);

        // 3. Status line
        String statusLine = max
            ? "§a" + Text.translatable("pc.label.max").getString()
            : locked
                ? "§c" + Text.translatable("pc.button.locked", e.requiredTier()).getString()
                : "§7" + Text.translatable("pc.label.level", lvl, e.maxLevel()).getString();

        // 4. Cost line (only when not locked/maxed)
        String costLine = (!max && !locked)
            ? (afford ? "§e" : "§c") + sym + formatMoney(cost)
            : null;

        // ── Measure tooltip box ────────────────────────────────────────────────
        int nameW   = textRenderer.getWidth(namePfx + nameRaw);
        int statusW = textRenderer.getWidth(statusLine.replaceAll("§.", ""));
        int costW   = costLine != null ? textRenderer.getWidth(costLine.replaceAll("§.", "")) : 0;
        int descW   = 0;
        for (OrderedText line : descWrapped) descW = Math.max(descW, textRenderer.getWidth(line));

        int contentW = Math.max(nameW, Math.max(statusW, Math.max(costW, descW)));
        int pad      = 6;
        int ttW      = contentW + pad * 2;
        int lineH    = textRenderer.fontHeight + 2;
        int ttH      = pad                              // top pad
                     + lineH                           // name
                     + 3                               // separator gap
                     + descWrapped.size() * lineH      // description lines
                     + lineH                           // status
                     + (costLine != null ? lineH : 0) // cost
                     + pad;                            // bottom pad

        // ── Position: prefer right of cursor, flip if near right/bottom edge ──
        int ttX = mx + 12;
        int ttY = my - 4;
        if (ttX + ttW > width  - 4) ttX = mx - ttW - 6;
        if (ttY + ttH > height - 4) ttY = height - ttH - 4;
        if (ttY < 4) ttY = 4;

        // ── Background layers ─────────────────────────────────────────────────
        // Outer dark border
        ctx.fill(ttX - 1, ttY - 1, ttX + ttW + 1, ttY + ttH + 1, 0xFF100010);
        // Inner background
        ctx.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xF0100010);
        // Left accent stripe matching card state
        TierTheme th  = theme();
        int stripeCol = locked ? th.stripeLocked : max ? th.stripeMaxed : th.stripeAvail;
        ctx.fill(ttX, ttY, ttX + 2, ttY + ttH, stripeCol);
        // Top/bottom thin border lines
        int stripeRgb = stripeCol & 0x00FFFFFF;
        ctx.fill(ttX + 2, ttY,           ttX + ttW, ttY + 1,      0x55000000 | stripeRgb);
        ctx.fill(ttX + 2, ttY + ttH - 1, ttX + ttW, ttY + ttH,   0x33000000 | stripeRgb);

        // ── Draw text ─────────────────────────────────────────────────────────
        int cx = ttX + pad;
        int cy = ttY + pad;

        // Name
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(namePfx + nameRaw), cx, cy, 0xFFFFFF);
        cy += lineH + 2;

        // Separator line
        ctx.fill(cx, cy, ttX + ttW - pad, cy + 1, 0x44FFFFFF);
        cy += 4;

        // Description lines
        for (OrderedText line : descWrapped) {
            ctx.drawTextWithShadow(textRenderer, line, cx, cy, 0xAAAAAA);
            cy += lineH;
        }
        if (!descWrapped.isEmpty()) cy += 2;

        // Status badge
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(statusLine), cx, cy, 0xFFFFFF);
        cy += lineH;

        // Cost
        if (costLine != null) {
            ctx.drawTextWithShadow(textRenderer,
                Text.literal(costLine), cx, cy, 0xFFFFFF);
        }
    }

    // ── Sabotage overlay ──────────────────────────────────────────────────────

    /**
     * Draws a modal-style warning panel over the card-list area when a sabotage
     * is active.  The panel is drawn outside the scissor region so it is never
     * clipped.  It dims the cards underneath and presents three resolution buttons.
     *
     * Layout (all Y values relative to {@code panelY}):
     *   +0   ┌────────────────────────────────────────┐
     *   +8   │  ⚠  SYSTEM COMPROMISED                 │
     *   +20  │  Passive income is blocked              │
     *   +32  │  Time remaining: 45s                    │
     *   +44  ├────────────────────────────────────────┤
     *   +50  │  [Fix Now ($X)] [Emerald] [Tech (15s)] │
     *   +68  └────────────────────────────────────────┘
     */
    private void drawSabotageOverlay(DrawContext ctx, int ox, int oy, int mx, int my) {
        int areaTop = oy + TAB_Y + TAB_H + 1;
        int areaBot = oy + GUI_H - FOOTER_H;
        int areaH   = areaBot - areaTop;

        // ── Dark-red wash covering the ENTIRE PC panel (header → footer) ──────
        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, 0xE8100505);

        // ── Central panel ─────────────────────────────────────────────────────
        int panelW  = GUI_W - 24;
        int panelH  = 72;
        int panelX  = ox + (GUI_W - panelW) / 2;
        int panelY  = areaTop + (areaH - panelH) / 2;

        // Shadow
        ctx.fill(panelX + 2, panelY + 2, panelX + panelW + 2, panelY + panelH + 2, 0x88000000);
        // Red border
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFFCC2222);
        // Panel background
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A0000);
        // Top/bottom accent lines
        ctx.fill(panelX, panelY,           panelX + panelW, panelY + 1,           0xFF993333);
        ctx.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF661111);

        int cx  = panelX + panelW / 2;
        int cy  = panelY + 8;
        int fh  = textRenderer.fontHeight;

        // ── Title ─────────────────────────────────────────────────────────────
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("pc.sabotage.title"),
            cx, cy, 0xFFFFFF);
        cy += fh + 2;

        // ── Subtitle ──────────────────────────────────────────────────────────
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("pc.sabotage.subtitle"),
            cx, cy, 0xFFFFFF);
        cy += fh + 2;

        // ── Timer / technician status ─────────────────────────────────────────
        int     timeLeft   = ClientSabotageCache.secondsRemaining();
        boolean techCalled = ClientSabotageCache.isTechnicianCalled();

        Text timerText = techCalled
            ? Text.translatable("pc.sabotage.tech_called")
            : Text.translatable("pc.sabotage.expires", timeLeft);
        ctx.drawCenteredTextWithShadow(textRenderer, timerText, cx, cy, 0xFFFFFF);
        cy += fh + 4;

        // ── Separator ─────────────────────────────────────────────────────────
        ctx.fill(panelX + 6, cy, panelX + panelW - 6, cy + 1, 0xFF441111);
        cy += 6;

        // ── Buttons ───────────────────────────────────────────────────────────
        int btnSpacing = 4;
        int totalBtnW  = panelW - 16;
        int btnW       = (totalBtnW - btnSpacing * 2) / 3;
        int btnH       = 14;
        int btn1X      = panelX + 8;
        int btn2X      = btn1X + btnW + btnSpacing;
        int btn3X      = btn2X + btnW + btnSpacing;
        int btnY       = cy;

        String sym     = ClientUpgradeCache.getCurrencySymbol();
        long   fixCost = ClientSabotageCache.penaltyAmount / 2;
        boolean canAfford = ClientPlayerDataCache.money >= fixCost;

        // Button 1 — Pay to fix
        boolean hov1 = mx >= btn1X && mx < btn1X + btnW && my >= btnY && my < btnY + btnH;
        int     bg1  = canAfford ? (hov1 ? 0xFFA01818 : 0xFF7B1111) : 0xFF3D2A2A;
        drawOverlayButton(ctx, btn1X, btnY, btnW, btnH, bg1, hov1,
            Text.translatable("pc.sabotage.btn.pay", sym + fixCost).getString());

        // Button 2 — Use Kit de Reparaciones
        boolean hov2 = mx >= btn2X && mx < btn2X + btnW && my >= btnY && my < btnY + btnH;
        int     bg2  = hov2 ? 0xFF1A6A7A : 0xFF0A3A4A;
        drawOverlayButton(ctx, btn2X, btnY, btnW, btnH, bg2, hov2,
            Text.translatable("pc.sabotage.btn.kit").getString());

        // Button 3 — Call Technician (roleplay — no countdown)
        boolean techDisabled = techCalled;
        boolean hov3 = !techDisabled && mx >= btn3X && mx < btn3X + btnW
                        && my >= btnY && my < btnY + btnH;
        int     bg3  = techDisabled ? 0xFF1A2A1A : (hov3 ? 0xFF2A6A2A : 0xFF1A4A1A);
        String  lbl3 = techCalled
            ? Text.translatable("pc.sabotage.btn.called").getString()
            : Text.translatable("pc.sabotage.btn.tech").getString();
        drawOverlayButton(ctx, btn3X, btnY, btnW, btnH, bg3, hov3 && !techDisabled, lbl3);
    }

    private void drawOverlayButton(DrawContext ctx, int x, int y, int w, int h,
                                   int bg, boolean hov, String label) {
        // Shadow
        ctx.fill(x + 1, y + 1, x + w + 1, y + h + 1, 0x55000000);
        // Background
        ctx.fill(x, y, x + w, y + h, bg);
        // Top highlight
        ctx.fill(x, y, x + w, y + 1, 0x22FFFFFF);
        // Hover glow
        if (hov) ctx.fill(x, y, x + w, y + h, 0x22FFFFFF);
        // Label — clip to button width
        String plain = label.replaceAll("§.", "");
        int    lw    = textRenderer.getWidth(plain);
        int    maxW  = w - 4;
        if (lw > maxW) {
            plain = textRenderer.trimToWidth(plain, maxW - textRenderer.getWidth("…")) + "…";
            label = plain;
            lw    = textRenderer.getWidth(plain);
        }
        ctx.drawTextWithShadow(textRenderer, Text.literal(label),
            x + (w - lw) / 2,
            y + (h - textRenderer.fontHeight) / 2,
            0xFFFFFF);
    }

    // ── Tier-up border pulse ──────────────────────────────────────────────────

    private void drawTierUpEffect(DrawContext ctx, int ox, int oy, TierTheme th, long now) {
        if (tierUpMs <= 0) return;
        long elapsed = now - tierUpMs;
        if (elapsed > 2200) { tierUpMs = 0; return; }
        float t     = elapsed / 2200f;
        int   alpha = (int)(220 * Math.sin(Math.PI * t));
        int   rgb   = th.accentLine & 0x00FFFFFF;
        for (int i = 0; i < 3; i++) {
            int a = alpha >> i;
            int c = (a << 24) | rgb;
            ctx.fill(ox - i,         oy - i,         ox + GUI_W + i, oy + 2 + i,       c);
            ctx.fill(ox - i,         oy + GUI_H - 2, ox + GUI_W + i, oy + GUI_H + i,   c);
            ctx.fill(ox - i,         oy - i,         ox + 2 + i,     oy + GUI_H + i,   c);
            ctx.fill(ox + GUI_W - 2, oy - i,         ox + GUI_W + i, oy + GUI_H + i,   c);
        }
    }

    private void drawOpenFade(DrawContext ctx, int ox, int oy, long now) {
        long e = now - openMs;
        if (e >= 350) return;
        int a = (int)(220 * (1f - e / 350f));
        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, (a << 24));
    }

    // ── Tier-up callback ──────────────────────────────────────────────────────

    public void onTierUp() {
        tierUpMs = System.currentTimeMillis();
        playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int ox = (width  - GUI_W) / 2;
        int oy = (height - GUI_H) / 2;

        // ── PC OFF: only the boot button works ────────────────────────────────
        if (!pcOn) {
            if (mx >= offBtnX && mx < offBtnX + offBtnW
                    && my >= offBtnY && my < offBtnY + offBtnH) {
                pcOn = true;
                openMs = System.currentTimeMillis(); // restart open-fade animation
                // Request a fresh money + data snapshot from the server the moment
                // the player boots the PC, so the display is always up-to-date.
                ClientPlayNetworking.send(new RequestSyncPayload());
                playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                playSound(SoundEvents.UI_BUTTON_CLICK, 0.4f, 1.2f);
            }
            return true; // consume all clicks when PC is off
        }

        // ── Header power button: turn PC OFF and close the screen ────────────
        if (mx >= gymBtnX && mx < gymBtnX + gymBtnW
                && my >= gymBtnY && my < gymBtnY + gymBtnH) {
            playSound(SoundEvents.UI_BUTTON_CLICK, 0.4f, 0.7f);
            this.close();
            return true;
        }

        // ── Sabotage overlay button handling ──────────────────────────────────
        // When active, the overlay covers the ENTIRE PC panel.
        // Only the 3 resolution buttons are interactive; everything else is blocked.
        if (ClientSabotageCache.active) {
            // Check if click is anywhere inside the PC panel
            if (mx >= ox && mx < ox + GUI_W && my >= oy && my < oy + GUI_H) {
                int areaTop = oy + TAB_Y + TAB_H + 1;
                int areaBot = oy + GUI_H - FOOTER_H;
                int areaH   = areaBot - areaTop;

                int panelW  = GUI_W - 24;
                int panelH  = 72;
                int panelX  = ox + (GUI_W - panelW) / 2;
                int panelY  = areaTop + (areaH - panelH) / 2;

                // Compute button Y — mirrors drawSabotageOverlay layout
                int fh   = textRenderer.fontHeight;
                int btnY = panelY + 8
                         + fh + 2   // title
                         + fh + 2   // subtitle
                         + fh + 4   // timer
                         + 1 + 6;   // separator (1px fill + 6 skip)

                int btnSpacing = 4;
                int totalBtnW  = panelW - 16;
                int btnW       = (totalBtnW - btnSpacing * 2) / 3;
                int btnH       = 14;
                int btn1X      = panelX + 8;
                int btn2X      = btn1X + btnW + btnSpacing;
                int btn3X      = btn2X + btnW + btnSpacing;

                if (my >= btnY && my < btnY + btnH) {
                    if (mx >= btn1X && mx < btn1X + btnW) {
                        // Pay button
                        long fixCost = ClientSabotageCache.penaltyAmount / 2;
                        if (ClientPlayerDataCache.money >= fixCost) {
                            ClientPlayNetworking.send(new SabotageActionPayload("pay"));
                            playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                        } else {
                            playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                        }
                        return true;
                    }
                    if (mx >= btn2X && mx < btn2X + btnW) {
                        // Emerald button
                        ClientPlayNetworking.send(new SabotageActionPayload("item"));
                        playSound(SoundEvents.UI_BUTTON_CLICK, 0.5f, 1.2f);
                        return true;
                    }
                    if (mx >= btn3X && mx < btn3X + btnW) {
                        // Technician button — only if not yet called
                        if (!ClientSabotageCache.isTechnicianCalled()) {
                            ClientPlayNetworking.send(new SabotageActionPayload("technician"));
                            playSound(SoundEvents.UI_BUTTON_CLICK, 0.5f, 0.8f);
                        } else {
                            playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.2f, 0.5f);
                        }
                        return true;
                    }
                }
                // Consume ALL other clicks inside the panel while sabotage is active
                return true;
            }
        }


        // Tab click
        List<String> cats = ClientUpgradeCache.getCategories();
        if (!cats.isEmpty()) {
            int tabW = evenTabWidth(cats.size());
            int tx = ox, ty = oy + TAB_Y;
            for (String cat : cats) {
                if (mx >= tx && mx < tx + tabW && my >= ty && my < ty + TAB_H) {
                    if (!cat.equalsIgnoreCase(currentCategory)) {
                        currentCategory = cat;
                        scrollOffset    = 0;
                        tabSwitchMs     = System.currentTimeMillis();
                        playSound(SoundEvents.UI_BUTTON_CLICK, 0.4f, 1.4f);
                    }
                    return true;
                }
                tx += tabW + TAB_GAP;
            }
        }

        if (!currentCategory.isEmpty()) {
            List<UpgradeClientEntry> upgrades = ClientUpgradeCache.getByCategory(currentCategory);
            int listTop    = oy + TAB_Y + TAB_H + 1 + LIST_PAD;
            int listBottom = oy + GUI_H - FOOTER_H - LIST_PAD;
            int visible    = visibleCards(listTop, listBottom);
            boolean needsScroll = upgrades.size() > visible;
            int cardW = GUI_W - CARD_MX * 2 - (needsScroll ? SB_W + SB_PAD : 0);
            int rx    = ox + CARD_MX;

            // Scrollbar track click
            if (needsScroll) {
                int sbX = ox + GUI_W - CARD_MX - SB_W;
                if (mx >= sbX && mx < sbX + SB_W && my >= listTop && my < listBottom) {
                    isDraggingSb = true;
                    applyScrollbarDrag((int) my, upgrades.size(), visible, listTop, listBottom);
                    return true;
                }
            }

            // Card button click
            int ry = listTop;
            for (int i = scrollOffset; i < upgrades.size(); i++) {
                if (ry + CARD_H > listBottom) break;
                UpgradeClientEntry e    = upgrades.get(i);
                int     lvl    = ClientPlayerDataCache.getUpgradeLevel(e.id());
                boolean max    = lvl >= e.maxLevel();
                boolean locked = ClientPlayerDataCache.tierLevel < e.requiredTier();
                String  lbl    = btnLabel(e, lvl, max, locked);
                int     bW     = dynBtnW(lbl);
                int     btnX   = rx + cardW - bW - BTN_MR;
                int     btnY   = ry + (CARD_H - BTN_H) / 2;

                if (mx >= btnX && mx < btnX + bW && my >= btnY && my < btnY + BTN_H) {
                    if (locked || max) {
                        playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.5f);
                    } else {
                        boolean afford = ClientPlayerDataCache.money >= e.getCostForLevel(lvl);
                        if (afford) {
                            flashId = e.id();
                            flashMs = System.currentTimeMillis();
                            playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                        } else {
                            playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                        }
                        ClientPlayNetworking.send(new UpgradeRequestPayload(e.id()));
                    }
                    return true;
                }
                ry += CARD_H + CARD_GAP;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (isDraggingSb && btn == 0 && !currentCategory.isEmpty()) {
            List<UpgradeClientEntry> upgrades = ClientUpgradeCache.getByCategory(currentCategory);
            applyScrollbarDrag((int) my, upgrades.size(), sbDragVisible, sbListTop, sbListBottom);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) isDraggingSb = false;
        return super.mouseReleased(mx, my, button);
    }

    private void applyScrollbarDrag(int mouseY, int total, int visible,
                                    int listTop, int listBottom) {
        if (total <= visible) return;
        int trackH = listBottom - listTop;
        int thumbH = Math.max(14, trackH * visible / total);
        int maxOff = trackH - thumbH;
        int relY   = mouseY - listTop - thumbH / 2;
        scrollOffset = Math.max(0, Math.min(total - visible,
            maxOff > 0 ? (int)((float) Math.max(0, relY) / maxOff * (total - visible)) : 0));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (!currentCategory.isEmpty()) {
            List<UpgradeClientEntry> upgrades = ClientUpgradeCache.getByCategory(currentCategory);
            int base       = (height - GUI_H) / 2;
            int listTop    = base + TAB_Y + TAB_H + 1 + LIST_PAD;
            int listBottom = base + GUI_H - FOOTER_H - LIST_PAD;
            int maxScroll  = Math.max(0, upgrades.size() - visibleCards(listTop, listBottom));
            scrollOffset   = Math.max(0, Math.min(maxScroll, scrollOffset + (vAmount < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    // ── Sound ─────────────────────────────────────────────────────────────────

    private void playSound(SoundEvent event, float volume, float pitch) {
        MinecraftClient.getInstance().getSoundManager()
            .play(PositionedSoundInstance.master(event, pitch, volume));
    }

    private void playSound(RegistryEntry<SoundEvent> entry, float volume, float pitch) {
        playSound(entry.value(), volume, pitch);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TierTheme theme() {
        return THEMES[Math.min(Math.max(tier() - 1, 0), THEMES.length - 1)];
    }

    private int tier() { return Math.max(1, ClientPlayerDataCache.tierLevel); }

    /** Compute button label given card state. */
    private String btnLabel(UpgradeClientEntry e, int lvl, boolean max, boolean locked) {
        if (locked) return Text.translatable("pc.button.locked", e.requiredTier()).getString();
        if (max)    return Text.translatable("pc.button.maxed").getString();
        return Text.translatable("pc.button.buy").getString();
    }

    /** Dynamic button width: always fits its label. */
    private int dynBtnW(String label) {
        return Math.max(BTN_W_MIN, textRenderer.getWidth(label) + BTN_PAD * 2);
    }

    private void drawClipped(DrawContext ctx, Text text, int x, int y, int maxW, int color) {
        if (maxW <= 0) return;
        String raw = text.getString();
        if (textRenderer.getWidth(raw) <= maxW) {
            ctx.drawTextWithShadow(textRenderer, text, x, y, color);
        } else {
            int    ew = textRenderer.getWidth("…");
            String tr = textRenderer.trimToWidth(raw, maxW - ew) + "…";
            ctx.drawTextWithShadow(textRenderer, Text.literal(tr), x, y, color);
        }
    }

    private int visibleCards(int listTop, int listBottom) {
        return Math.max(1, (listBottom - listTop) / (CARD_H + CARD_GAP));
    }

    private int evenTabWidth(int n) {
        return (GUI_W - (n - 1) * TAB_GAP) / n;
    }

    private static String categoryKey(String cat) {
        return "pc.category." + cat.toLowerCase().replace(' ', '_');
    }

    private static String categoryIcon(String cat) {
        return switch (cat.toLowerCase().replace(' ', '_')) {
            case "visual"         -> "§b■";
            case "staff"          -> "§a◆";
            case "passive_income" -> "§e●";
            case "stat"           -> "§c▲";
            default               -> "§7◉";
        };
    }

    private static int blendColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
            | ((int)(ar + (br - ar) * t) << 16)
            | ((int)(ag + (bg - ag) * t) <<  8)
            |  (int)(ab + (bb - ab) * t);
    }

    private static int blendDark(int color, float factor) {
        int r = (int)(((color >> 16) & 0xFF) * factor);
        int g = (int)(((color >>  8) & 0xFF) * factor);
        int b = (int)(( color        & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lightenRgb(int color, int add) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + add);
        int g = Math.min(255, ((color >>  8) & 0xFF) + add);
        int b = Math.min(255, ( color        & 0xFF) + add);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String formatMoney(long amount) {
        if (amount < 1_000) return String.valueOf(amount);
        StringBuilder sb = new StringBuilder(Long.toString(amount));
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ',');
        return sb.toString();
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mx, int my) {}

    // ── TierTheme ─────────────────────────────────────────────────────────────

    private static final class TierTheme {
        final int bg, headerTop, headerBot, accentLine, panelBorder;
        final int tab, tabHov, tabUnderline;
        final int cardBg, cardHov, cardBorder, cardHovBorder;
        final int stripeAvail, stripeMaxed, stripeLocked;
        final int btnBuy, btnBuyHov;
        final boolean hasGlow, hasPulse;

        TierTheme(int bg, int headerTop, int headerBot, int accentLine, int panelBorder,
                  int tab, int tabHov, int tabUnderline,
                  int cardBg, int cardHov, int cardBorder, int cardHovBorder,
                  int stripeAvail, int stripeMaxed, int stripeLocked,
                  int btnBuy, int btnBuyHov,
                  boolean hasGlow, boolean hasPulse) {
            this.bg = bg; this.headerTop = headerTop; this.headerBot = headerBot;
            this.accentLine = accentLine; this.panelBorder = panelBorder;
            this.tab = tab; this.tabHov = tabHov; this.tabUnderline = tabUnderline;
            this.cardBg = cardBg; this.cardHov = cardHov;
            this.cardBorder = cardBorder; this.cardHovBorder = cardHovBorder;
            this.stripeAvail = stripeAvail; this.stripeMaxed = stripeMaxed;
            this.stripeLocked = stripeLocked;
            this.btnBuy = btnBuy; this.btnBuyHov = btnBuyHov;
            this.hasGlow = hasGlow; this.hasPulse = hasPulse;
        }
    }
}
