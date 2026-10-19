package uk.iwaservice.traincab;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import uk.iwaservice.traincab.client.AdvancedCabHandle;
import uk.iwaservice.traincab.mascon.TrainCabNetwork;

@Mod(TrainCabMod.MODID)
public class TrainCabMod {

    public static final String MODID = "traincab";

    public TrainCabMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TrainCabConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TrainCabServerConfig.SPEC);
        TrainCabNetwork.register();
        TrainCabItems.register(FMLJavaModLoadingContext.get().getModEventBus());
        if (FMLEnvironment.dist.isClient()) {
            AdvancedCabHandle.init();
        }
    }
}
