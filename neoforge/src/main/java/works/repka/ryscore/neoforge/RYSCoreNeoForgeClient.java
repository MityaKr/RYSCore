package works.repka.ryscore.neoforge;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import works.repka.ryscore.RYSCore;
import works.repka.ryscore.theme.ThemeManager;

/** Клиентская часть кора: темы перечитываются вместе с ресурсами, модам семьи делать ничего не нужно. */
@Mod(value = RYSCore.MOD_ID, dist = Dist.CLIENT)
public final class RYSCoreNeoForgeClient {
    public RYSCoreNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((ResourceManagerReloadListener) ThemeManager::reload));
    }
}
