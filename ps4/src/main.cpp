#include "addon_importer.hpp"
#include "addonstation_api.hpp"
#include "config.hpp"
#include "demo_data.hpp"
#include "minecraft_storage.hpp"
#include "orbis_http_transport.hpp"
#include "ui.hpp"

#include <orbis/libkernel.h>

#include <SDL2/SDL.h>

#include <atomic>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <utility>

namespace {

struct SharedWorkerState {
    std::mutex mutex;
    addonstation::CatalogSnapshot catalog;
    std::string status;
    bool connected = false;
    bool loadedOnce = false;
    bool demoMode = true;
    std::uint32_t revision = 0;
    std::atomic<bool> loading{false};
    std::atomic<bool> refreshRequested{false};
    // 0 = idle, 1 = queued by an explicit user action, 2 = importing.
    std::atomic<int> packImportState{0};
    std::atomic<bool> stopping{false};
};

void setInitialError(ps4app::UiState& ui, const std::string& message) {
    ui.status = "DEMO MODE: fictional sample list only. " + message;
    ui.connected = false;
    ui.demoMode = true;
}

constexpr char kBundledBackroomsMcpack[] = "/app0/assets/Backrooms-1.1-B.mcpack";
constexpr char kPackImportHint[] = " SQUARE: install bundled Backrooms; existing packs are never overwritten.";

std::string installBundledBackrooms() {
    const ps4app::addon_importer::Inspection inspection =
        ps4app::addon_importer::inspectMcpack(kBundledBackroomsMcpack);
    if (!inspection.ok()) {
        return std::string("Backrooms preflight failed (") +
               ps4app::addon_importer::errorName(inspection.error) + "): " + inspection.message;
    }
    if (inspection.packKind != ps4app::addon_importer::PackKind::Resource) {
        return "Backrooms is not a resource-only pack; nothing was written.";
    }

    ps4app::minecraft_storage::LocateResult storage;
    constexpr int kLocateAttempts = 40;
    for (int attempt = 0; attempt < kLocateAttempts; ++attempt) {
        storage = ps4app::minecraft_storage::locateComMojangDirectories(
            ps4app::minecraft_storage::ps4AppTemporaryRootCandidates());
        if (storage.ok() || storage.error == ps4app::minecraft_storage::LocateError::Ambiguous ||
            storage.error == ps4app::minecraft_storage::LocateError::InvalidRoot) {
            break;
        }
        sceKernelUsleep(250 * 1000);
    }
    if (!storage.ok()) {
        if (storage.error == ps4app::minecraft_storage::LocateError::Ambiguous) {
            return "Backrooms not installed: multiple Minecraft profiles were found; none was selected.";
        }
        return "No Minecraft com.mojang mount appeared under /app_tmp or /app_temp after 10 seconds. "
               "Keep Minecraft open or suspended, then press Square to retry.";
    }

    std::string destination;
    std::string destinationError;
    if (!ps4app::minecraft_storage::packDestinationDirectory(
            storage.locations.front().comMojangDirectory,
            inspection.packKind,
            inspection.packUuid,
            destination,
            destinationError)) {
        return "Backrooms not installed: " + destinationError;
    }

    ps4app::addon_importer::ImportRequest request;
    request.archivePath = kBundledBackroomsMcpack;
    request.destinationDirectory = destination;
    // This function is only queued by an explicit Square-button/I-key action.
    request.writeApproved = true;
    const ps4app::addon_importer::ImportResult result =
        ps4app::addon_importer::importMcpack(request);
    if (!result.ok()) {
        if (result.error == ps4app::addon_importer::Error::DestinationAlreadyExists) {
            return "Backrooms is already present; its existing resource_packs folder was left unchanged.";
        }
        return std::string("Backrooms import failed (") +
               ps4app::addon_importer::errorName(result.error) + "): " + result.message;
    }

    return "Backrooms installed to " + destination + " (" +
           std::to_string(result.filesWritten) + " files).";
}

} // namespace

int main(int, char**) {
    setvbuf(stdout, nullptr, _IONBF, 0);

    OrbisHttpTransport transport;
    addonstation::AddonStationApi api(
        transport,
        ps4app::config::kSupabaseProjectUrl,
        ps4app::config::kSupabasePublishableKey);

    ps4app::UiState ui;
    ui.catalog = ps4app::makeDemoCatalog();
    ui.loadedOnce = true;
    ui.demoMode = true;
    ui.status = "DEMO MODE: fictional sample data only; no database writes are made.";

    bool transportReady = false;
    if (!api.isConfigured()) {
        setInitialError(ui, api.configurationError());
    } else {
        std::string networkError;
        transportReady = transport.initialize(networkError);
        if (!transportReady) setInitialError(ui, networkError);
        else ui.status = "DEMO MODE: showing samples while checking the shared Supabase catalog...";
    }

    const ps4app::minecraft_storage::LocateResult minecraftStorage =
        ps4app::minecraft_storage::locateComMojangDirectories(
            ps4app::minecraft_storage::ps4AppTemporaryRootCandidates());
    std::string minecraftStorageStatus;
    if (minecraftStorage.ok()) {
        minecraftStorageStatus = " Minecraft pack storage detected.";
    } else if (minecraftStorage.error == ps4app::minecraft_storage::LocateError::Ambiguous) {
        minecraftStorageStatus = " Multiple Minecraft profiles detected; no profile was selected.";
    } else {
        minecraftStorageStatus = " Minecraft storage was not resolved.";
    }
    ui.status += minecraftStorageStatus + kPackImportHint;

    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_JOYSTICK) != 0) {
        std::printf("SDL initialization failed: %s\n", SDL_GetError());
        transport.shutdown();
        return 1;
    }

    SDL_Window* window = SDL_CreateWindow(
        "AddonStation PS4 Preview",
        SDL_WINDOWPOS_UNDEFINED,
        SDL_WINDOWPOS_UNDEFINED,
        1920,
        1080,
        0);
    if (!window) {
        std::printf("Could not create the PS4 application window: %s\n", SDL_GetError());
        SDL_Quit();
        transport.shutdown();
        return 1;
    }

    SDL_Surface* windowSurface = SDL_GetWindowSurface(window);
    SDL_Renderer* renderer = windowSurface ? SDL_CreateSoftwareRenderer(windowSurface) : nullptr;
    if (!renderer) {
        std::printf("Could not create the SDL renderer: %s\n", SDL_GetError());
        SDL_DestroyWindow(window);
        SDL_Quit();
        transport.shutdown();
        return 1;
    }

    ps4app::initializeUiAssets(renderer);

    SDL_Joystick* controller = nullptr;
    if (SDL_NumJoysticks() > 0) {
        controller = SDL_JoystickOpen(0);
        if (!controller) std::printf("Controller unavailable: %s\n", SDL_GetError());
    }
    SDL_JoystickEventState(SDL_ENABLE);

    SharedWorkerState shared;
    shared.status = ui.status;
    shared.catalog = ui.catalog;
    shared.connected = ui.connected;
    shared.loadedOnce = ui.loadedOnce;
    shared.demoMode = ui.demoMode;
    std::thread worker;
    if (api.isConfigured() && transportReady) {
        shared.refreshRequested.store(true);
        worker = std::thread([&api, &shared]() {
            while (!shared.stopping.load()) {
                if (shared.refreshRequested.exchange(false)) {
                    shared.loading.store(true);
                    addonstation::FetchResult result = api.fetchCatalog();
                    {
                        std::lock_guard<std::mutex> lock(shared.mutex);
                        shared.connected = result.ok;
                        if (result.ok) {
                            shared.catalog = std::move(result.snapshot);
                            shared.loadedOnce = true;
                            shared.demoMode = false;
                            shared.status = result.message;
                        } else if (shared.demoMode) {
                            shared.status = "Live catalog unavailable; fictional DEMO DATA remains on screen. " +
                                            result.message;
                        } else {
                            shared.status = result.message;
                        }
                        ++shared.revision;
                    }
                    shared.loading.store(false);
                }
                sceKernelUsleep(50 * 1000);
            }
        });
    }

    std::thread packImportWorker([&shared]() {
        while (!shared.stopping.load()) {
            int expected = 1;
            if (shared.packImportState.compare_exchange_strong(expected, 2)) {
                {
                    std::lock_guard<std::mutex> lock(shared.mutex);
                    shared.status = "Waiting up to 10 seconds for Minecraft's temporary CUSA/user mount, then importing Backrooms...";
                    ++shared.revision;
                }
                const std::string status = installBundledBackrooms();
                {
                    std::lock_guard<std::mutex> lock(shared.mutex);
                    shared.status = status;
                    ++shared.revision;
                }
                shared.packImportState.store(0);
            }
            sceKernelUsleep(50 * 1000);
        }
    });

    bool running = true;
    std::uint32_t shownRevision = 0;
    while (running) {
        SDL_Event event;
        while (SDL_PollEvent(&event)) {
            ps4app::UiActions actions;
            ps4app::handleInput(event, ui, actions);
            if (actions.quit) running = false;
            if (actions.requestRefresh && api.isConfigured() && transportReady &&
                !shared.loading.load()) {
                shared.refreshRequested.store(true);
                ui.status = "Refresh requested from the shared database...";
            }
            if (actions.requestPackImport) {
                int expected = 0;
                if (shared.packImportState.compare_exchange_strong(expected, 1)) {
                    ui.status = "Backrooms import queued; the pack will use its manifest UUID folder.";
                } else {
                    ui.status = "Backrooms import is already queued or in progress.";
                }
            }
        }

        {
            std::lock_guard<std::mutex> lock(shared.mutex);
            if (shared.revision != shownRevision) {
                ui.status = shared.status + minecraftStorageStatus + kPackImportHint;
                ui.connected = shared.connected;
                ui.loadedOnce = shared.loadedOnce;
                ui.demoMode = shared.demoMode;
                if (shared.loadedOnce) ui.catalog = shared.catalog;
                shownRevision = shared.revision;
            }
        }
        ui.loading = shared.loading.load();

        ps4app::renderUi(renderer, ui);
        SDL_RenderPresent(renderer);
        SDL_UpdateWindowSurface(window);
        SDL_Delay(16);
    }

    shared.stopping.store(true);
    if (worker.joinable()) worker.join();
    if (packImportWorker.joinable()) packImportWorker.join();
    transport.shutdown();

    if (controller) SDL_JoystickClose(controller);
    ps4app::shutdownUiAssets();
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
