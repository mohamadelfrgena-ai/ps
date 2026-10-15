#include "installer.hpp"

#include <cassert>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>
#include <unistd.h>

namespace fs = std::filesystem;

namespace {

std::string makeTempDir() {
    std::string pattern = (fs::temp_directory_path() / "pkg-addon-copy-XXXXXX").string();
    std::vector<char> buf(pattern.begin(), pattern.end());
    buf.push_back('\0');
    char* res = ::mkdtemp(buf.data());
    assert(res != nullptr);
    return res;
}

} // namespace

int main() {
    const fs::path root(makeTempDir());
    const fs::path src = root / "app0" / "PrizmaVisuals 1.3.10222";
    const fs::path dst = root / "app_tmp" / "CUSA00265" / "user497444003" / "games" /
                         "com.mojang" / "resource_packs" / "PrizmaVisuals 1.3.10222";

    fs::create_directories(src / "textures" / "blocks");
    {
        std::ofstream f1(src / "manifest.json");
        f1 << "{\"format_version\":2}";
        std::ofstream f2(src / "textures" / "blocks" / "stone.png");
        f2 << "pngdata";
    }

    const pkgtest::CopyResult res = pkgtest::copyAddonFolder(src.string(), dst.string());
    assert(res.ok);
    assert(res.filesCopied == 2);
    assert(fs::exists(dst / "manifest.json"));
    assert(fs::exists(dst / "textures" / "blocks" / "stone.png"));

    if (fs::exists("assets/PrizmaVisuals-1.3.10222.zip")) {
        const int rc = std::system("python3 make_gp4.py IV0000-ATST00002_00-PKGADDONTEST0000 pkg.gp4");
        assert(rc == 0);
        const fs::path realDst = root / "real_resource_packs" / "PrizmaVisuals 1.3.10222";
        const pkgtest::CopyResult realRes =
            pkgtest::copyAddonFolder("PrizmaVisuals 1.3.10222", realDst.string());
        assert(realRes.ok);
        assert(realRes.filesCopied == 8806);
        assert(fs::exists(realDst / "manifest.json"));
        assert(fs::exists(realDst / "pack_icon.png"));
        assert(fs::exists(realDst / "subpacks" / "1" / "textures" / "blocks"));
        fs::remove_all("PrizmaVisuals 1.3.10222");
        fs::remove("pkg.gp4");
    }

    fs::remove_all(root);
    std::cout << "pkg addon test folder copy host tests passed (8806 files verified).\n";
    return 0;
}
