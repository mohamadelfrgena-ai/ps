#pragma once

#include "addonstation_api.hpp"

#include <SDL2/SDL.h>

#include <cstddef>
#include <string>

namespace ps4app {

enum class CatalogTab {
    Content,
    Plans,
};

struct UiState {
    addonstation::CatalogSnapshot catalog;
    CatalogTab tab = CatalogTab::Content;
    std::size_t selectedContent = 0;
    std::size_t selectedPlan = 0;
    bool loading = false;
    bool connected = false;
    bool loadedOnce = false;
    bool demoMode = false;
    std::string status = "Waiting for configuration.";
};

struct UiActions {
    bool requestRefresh = false;
    bool quit = false;
};

void handleInput(const SDL_Event& event, UiState& state, UiActions& actions);
void renderUi(SDL_Renderer* renderer, const UiState& state);

} // namespace ps4app
