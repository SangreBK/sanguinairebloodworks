package com.sanguinaire.registry;

import com.sanguinaire.Sanguinaire;
import com.sanguinaire.menu.ResearchStationMenu;
import com.sanguinaire.menu.SanguineAlembicMenu;
import com.sanguinaire.menu.SerumBriefcaseMenu;
import com.sanguinaire.menu.SerumSynthesizerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Sanguinaire.MOD_ID);

    public static final RegistryObject<MenuType<SerumBriefcaseMenu>> SERUM_BRIEFCASE = MENUS.register("serum_briefcase",
            () -> IForgeMenuType.create(SerumBriefcaseMenu::new));

    public static final RegistryObject<MenuType<ResearchStationMenu>> RESEARCH_STATION = MENUS.register("research_station",
            () -> IForgeMenuType.create(ResearchStationMenu::new));

    public static final RegistryObject<MenuType<SerumSynthesizerMenu>> SERUM_SYNTHESIZER = MENUS.register("serum_synthesizer",
            () -> IForgeMenuType.create(SerumSynthesizerMenu::new));

    public static final RegistryObject<MenuType<SanguineAlembicMenu>> SANGUINE_ALEMBIC = MENUS.register("sanguine_alembic",
            () -> IForgeMenuType.create(SanguineAlembicMenu::new));

    private ModMenuTypes() {
    }
}
