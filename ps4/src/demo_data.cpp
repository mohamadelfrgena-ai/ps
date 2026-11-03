#include "demo_data.hpp"

#include <utility>

namespace ps4app {

addonstation::CatalogSnapshot makeDemoCatalog() {
    addonstation::CatalogSnapshot catalog;
    catalog.content = {
        {"demo-addon-01", "Crystal Isles - DEMO ADD-ON",
         "Fictional sample entry for testing the PS4 catalog layout.",
         "addon", "free", "0.1-demo", 128, 640},
        {"demo-world-02", "Sky Village - DEMO WORLD",
         "A pretend floating village used only as placeholder content.",
         "world", "epic", "0.1-demo", 76, 402},
        {"demo-pack-03", "Pixel Armor - TEST RESOURCE PACK",
         "Fake resource-pack metadata. No Minecraft file is included.",
         "resource_pack", "free", "0.1-demo", 53, 291},
        {"demo-map-04", "Lost Temple - DEMO MAP",
         "Sample map description for checking selection and wrapping.",
         "map", "legendary", "0.1-demo", 31, 180},
        {"demo-addon-05", "Tiny Companions - TEST ADD-ON",
         "Another fictional entry; this row does not come from Supabase.",
         "addon", "epic", "0.1-demo", 19, 99},
        {"demo-world-06", "Survival Island - DEMO WORLD",
         "Offline demo data only. Press Triangle to try a live refresh.",
         "world", "free", "0.1-demo", 12, 64},
    };
    catalog.plans = {
        {"free", "FREE - DEMO", "Example plan for layout testing.", "0", "0", "day", "5", 30},
        {"epic", "EPIC - DEMO", "Example paid-tier text; no checkout is available.", "15", "2.99", "day", "25", 30},
        {"legendary", "LEGENDARY - DEMO", "Fictional plan data for screen testing only.", "30", "5.99", "none", "unlimited", 30},
    };
    return catalog;
}

} // namespace ps4app
