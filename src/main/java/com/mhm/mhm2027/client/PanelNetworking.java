package com.mhm.mhm2027.client;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.network.EditorCheck;
import com.mhm.mhm2027.network.EditorFile;
import com.mhm.mhm2027.network.PanelMessage;
import com.mhm.mhm2027.network.PanelSnapshot;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The client side of the panel's packets. */
@EventBusSubscriber(modid = Mhm2027.MODID, value = Dist.CLIENT)
public final class PanelNetworking {
    private PanelNetworking() {
    }

    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(PanelSnapshot.TYPE, (snapshot, context) -> PanelState.accept(snapshot));
        event.register(PanelMessage.TYPE, (message, context) -> PanelState.accept(message));
        event.register(EditorFile.TYPE, (file, context) -> EditorState.accept(file));
        event.register(EditorCheck.TYPE, (check, context) -> EditorState.accept(check));
    }

    /** Don't show one world's terminal in the next. */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PanelState.reset();
        EditorState.reset();
    }
}
