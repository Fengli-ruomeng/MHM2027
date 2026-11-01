package com.mhm.mhm2027.client;

import com.mhm.mhm2027.network.PanelRequest;
import com.mhm.mhm2027.terminal.Terminal;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * What G opens while the gateway is off or booting: the boot sequence, full screen. When the
 * boot finishes it becomes the Edge desktop. You may close it at any time, but the boot carries on
 * without you watching it, and you're left blind and heavily slowed until it's done.
 */
public class EdgeBootScreen extends Screen {
    /** If the server hasn't started a boot by then (it refused), give up. */
    private static final long WAIT_MILLIS = 2500;

    private final KeyMapping toggleKey;
    private final long openedAt = Util.getMillis();
    private boolean seenBoot;
    private boolean finished;

    public EdgeBootScreen(KeyMapping toggleKey) {
        super(Component.translatable("mhm2027.boot.title"));
        this.toggleKey = toggleKey;
    }

    /** The game keeps running while the gateway boots. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (HudFeed.boot(now) >= 0) {
            seenBoot = true;
            return;
        }
        if (seenBoot || HudFeed.power() == Terminal.Power.ON) {
            finished = true;
            minecraft.setScreen(new EdgeDesktopScreen());
        } else if (now - openedAt > WAIT_MILLIS) {
            finished = true;
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        long now = Util.getMillis();
        LensHud.bootScreen(graphics, font, width, height, now, Math.max(0, HudFeed.boot(now)));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (toggleKey.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (!finished) {
            ClientPacketDistributor.sendToServer(PanelRequest.of(PanelRequest.Action.LOOK_AWAY));
        }
        super.removed();
    }
}
