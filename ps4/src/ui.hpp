#pragma once

#include "addonstation_api.hpp"

#include <SDL2/SDL.h>

#include <cstddef>
#include <string>

namespace ps4app {

enum class CatalogTab {
    Home,
    Deals,
    Store,
    Chat,
    Settings,
};

struct UiState {
    addonstation::CatalogSnapshot catalog;
    CatalogTab tab = CatalogTab::Home;
    std::size_t selectedContent = 0;
    std::size_t selectedPlan = 0;
    int contentFilter = 0; // 0 = all, 1 = add-ons/resource packs, 2 = worlds
    bool loading = false;
    bool connected = false;
    bool loadedOnce = false;
    bool demoMode = false;
    std::string status = "Waiting for configuration.";
};

struct UiActions {
    bool requestRefresh = false;
    bool requestPackImport = false;
    bool quit = false;
};

void initializeUiAssets(SDL_Renderer* renderer);
void shutdownUiAssets();
void handleInput(const SDL_Event& event, UiState& state, UiActions& actions);
void renderUi(SDL_Renderer* renderer, const UiState& state);

} // namespace ps4app
