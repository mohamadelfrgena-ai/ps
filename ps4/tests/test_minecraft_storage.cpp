#include "minecraft_storage.hpp"

#include <cassert>
#include <filesystem>
#include <iostream>
#include <string>
#include <vector>
#include <unistd.h>

namespace {

namespace fs = std::filesystem;

std::string makeTempDirectory() {
    std::string pattern = (fs::temp_directory_path() / "minecraft-storage-test-XXXXXX").string();
    std::vector<char> writable(pattern.begin(), pattern.end());
    writable.push_back('\0');
    char* path = ::mkdtemp(writable.data());
    assert(path != nullptr);
    return path;
}

void makeMojangTree(const fs::path& root,
                    const std::string& appRootName,
                    const std::string& titleId,
                    const std::string& userId) {
    const fs::path mojang = root / appRootName / titleId / userId / "games" / "com.mojang";
    fs::create_directories(mojang / "resource_packs");
    fs::create_directories(mojang / "behavior_packs");
}

void testSingleDynamicTitleAndUserDirectory() {
    const fs::path root(makeTempDirectory());
    makeMojangTree(root, "app_tmp", "CUSA12345", "user123456789012");

    const ps4app::minecraft_storage::LocateResult result =
        ps4app::minecraft_storage::locateComMojangDirectories({(root / "app_tmp").string()});
    assert(result.ok());
    assert(result.locations.size() == 1);
    assert(result.locations[0].titleDirectory == "CUSA12345");
    assert(result.locations[0].userDirectory == "user123456789012");
    assert(result.locations[0].comMojangDirectory ==
           (root / "app_tmp/CUSA12345/user123456789012/games/com.mojang").string());

    fs::remove_all(root);
}

void testBothProvidedPs4RootSpellingsAndAmbiguity() {
    const fs::path root(makeTempDirectory());
    makeMojangTree(root, "app_tmp", "CUSA10001", "user1");
    makeMojangTree(root, "app_temp", "CUSA10002", "user123456789");

    const ps4app::minecraft_storage::LocateResult result =
        ps4app::minecraft_storage::locateComMojangDirectories({
            (root / "app_tmp").string(), (root / "app_temp").string()});
    assert(!result.ok());
    assert(result.error == ps4app::minecraft_storage::LocateError::Ambiguous);
    assert(result.locations.size() == 2);

    fs::remove_all(root);
}

void testNonNumericNamesAndSymlinksAreIgnored() {
    const fs::path root(makeTempDirectory());
    const fs::path target = root / "outside";
    makeMojangTree(root, "outside", "CUSA54321", "user987654321");
    fs::create_directories(root / "app_tmp");
    fs::create_directory_symlink(target / "CUSA54321", root / "app_tmp/CUSA54321");
    makeMojangTree(root, "app_tmp", "CUSA-not-numeric", "user123");
    makeMojangTree(root, "app_tmp", "CUSA24680", "user-not-numeric");

    const ps4app::minecraft_storage::LocateResult result =
        ps4app::minecraft_storage::locateComMojangDirectories({(root / "app_tmp").string()});
    assert(!result.ok());
    assert(result.error == ps4app::minecraft_storage::LocateError::NotFound);

    fs::remove_all(root);
}

void testBroadFilesystemRootIsRefused() {
    const ps4app::minecraft_storage::LocateResult result =
        ps4app::minecraft_storage::locateComMojangDirectories({"/"});
    assert(!result.ok());
    assert(result.error == ps4app::minecraft_storage::LocateError::InvalidRoot);
}

void testPackDestinationRouting() {
    const fs::path root(makeTempDirectory());
    const fs::path mojang = root / "app_tmp/CUSA99999/user7/games/com.mojang";
    fs::create_directories(mojang / "resource_packs");
    fs::create_directories(mojang / "behavior_packs");

    const std::string uuid = "123e4567-e89b-12d3-a456-426614174000";
    std::string destination;
    std::string error;
    assert(ps4app::minecraft_storage::packDestinationDirectory(
        mojang.string(), ps4app::addon_importer::PackKind::Resource, uuid, destination, error));
    assert(destination == (mojang / "resource_packs" / uuid).string());

    assert(ps4app::minecraft_storage::packDestinationDirectory(
        mojang.string(), ps4app::addon_importer::PackKind::Behavior, uuid, destination, error));
    assert(destination == (mojang / "behavior_packs" / uuid).string());

    assert(!ps4app::minecraft_storage::packDestinationDirectory(
        mojang.string(), ps4app::addon_importer::PackKind::Mixed, uuid, destination, error));
    assert(!ps4app::minecraft_storage::packDestinationDirectory(
        mojang.string(), ps4app::addon_importer::PackKind::Resource, "../bad", destination, error));
    assert(!ps4app::minecraft_storage::packDestinationDirectory(
        (root / "not-com-mojang").string(), ps4app::addon_importer::PackKind::Resource,
        uuid, destination, error));

    fs::remove_all(root);
}

} // namespace

int main() {
    const std::vector<std::string> roots = ps4app::minecraft_storage::ps4AppTemporaryRootCandidates();
    assert(roots.size() == 2);
    assert(roots[0] == "/app_tmp");
    assert(roots[1] == "/app_temp");

    testSingleDynamicTitleAndUserDirectory();
    testBothProvidedPs4RootSpellingsAndAmbiguity();
    testNonNumericNamesAndSymlinksAreIgnored();
    testBroadFilesystemRootIsRefused();
    testPackDestinationRouting();
    std::cout << "PS4 Minecraft storage locator host tests passed (temporary fixtures only).\n";
    return 0;
}
