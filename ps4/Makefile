# AddonStation PS4 Preview — OpenOrbis PS4 Toolchain / Linux or macOS
TITLE       := AddonStation PS4 Preview
VERSION     := 0.2
TITLE_ID    := ASTN00001
CONTENT_ID  := IV0000-ASTN00001_00-ADDONSTATION0000

TOOLCHAIN   ?= $(OO_PS4_TOOLCHAIN)
UNAME_S     := $(shell uname -s)
ifeq ($(UNAME_S),Linux)
  TOOL_OS   := linux
  CC        := clang
  CXX       := clang++
  LD        := ld.lld
else ifeq ($(UNAME_S),Darwin)
  TOOL_OS   := macos
  CC        := clang
  CXX       := clang++
  LD        := ld.lld
else
  TOOL_OS   := unsupported
endif
HOST_CXX    ?= c++
HOST_CC     ?= cc

PKG_TOOL    := $(TOOLCHAIN)/bin/$(TOOL_OS)/PkgTool.Core
CREATE_GP4  := $(TOOLCHAIN)/bin/$(TOOL_OS)/create-gp4
CREATE_FSELF:= $(TOOLCHAIN)/bin/$(TOOL_OS)/create-fself
OBJ_DIR     := build/obj
ELF_FILE    := build/addonstation-ps4.elf
OELF_FILE   := build/addonstation-ps4.oelf
PKG_FILE    := $(CONTENT_ID).pkg
OO_SDL_SAMPLE := $(TOOLCHAIN)/samples/SDL2
RUNTIME_FILES := sce_module/libc.prx sce_module/libSceFios2.prx \
                 sce_sys/about/right.sprx
BUNDLED_ADDON_ASSETS := assets/Backrooms-1.1-B.mcpack assets/Backrooms-1.1-B-cover.jpg assets/Backrooms-1.1-B-cover.bmp
PKG_CONTENTS := eboot.bin sce_sys/param.sfo sce_sys/icon0.png $(RUNTIME_FILES) $(BUNDLED_ADDON_ASSETS)
SOURCES        := $(wildcard src/*.cpp)
CXX_OBJECTS    := $(patsubst src/%.cpp,$(OBJ_DIR)/%.o,$(SOURCES))
MINIZ_SOURCES  := third_party/miniz/miniz.c third_party/miniz/miniz_zip.c \
                  third_party/miniz/miniz_tinfl.c third_party/miniz/miniz_tdef.c
MINIZ_OBJECTS  := $(patsubst third_party/miniz/%.c,$(OBJ_DIR)/miniz_%.o,$(MINIZ_SOURCES))
OBJECTS        := $(CXX_OBJECTS) $(MINIZ_OBJECTS)
HOST_MINIZ_OBJECTS := $(patsubst third_party/miniz/%.c,build-host/miniz_%.o,$(MINIZ_SOURCES))

# SDL2 provides the 1080p UI and DS4 input. SceHttp/SceSsl/SceNet provide
# HTTPS REST access; the client deliberately has no database write operations.
LIBS        := -lc -lkernel -lc++ -lSceUserService -lSceVideoOut -lSceAudioOut \
               -lScePad -lSceSysmodule -lSDL2 -lSceNet -lSceSsl -lSceHttp
COMMONFLAGS := --target=x86_64-pc-freebsd12-elf -fPIC -funwind-tables \
               -isysroot $(TOOLCHAIN) -isystem $(TOOLCHAIN)/include
CXXFLAGS    := $(COMMONFLAGS) -std=c++17 -fexceptions -D_POSIX_C_SOURCE=200809L -D_GNU_SOURCE \
               -isystem $(TOOLCHAIN)/include/c++/v1 -Ithird_party/miniz
CFLAGS      := $(COMMONFLAGS) -std=c99 -fPIC -D_POSIX_C_SOURCE=200809L -D_GNU_SOURCE
LDFLAGS     := -m elf_x86_64 -pie --script $(TOOLCHAIN)/link.x --eh-frame-hdr \
               -L$(TOOLCHAIN)/lib $(LIBS) $(TOOLCHAIN)/lib/crt1.o

.PHONY: all pkg clean check-toolchain prepare-runtime host-test host-test-api host-test-importer host-test-storage objects
all: pkg

# Compile target translation units only; does not link, package, or create eboot.bin.
objects: $(OBJECTS)

pkg: check-toolchain $(PKG_FILE)

check-toolchain:
	@test -n "$(TOOLCHAIN)" || { echo "Set OO_PS4_TOOLCHAIN to your OpenOrbis toolchain directory."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/PkgTool.Core" || { echo "OpenOrbis package tools not found under $(TOOLCHAIN)/bin/$(TOOL_OS)."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/create-gp4" || { echo "OpenOrbis create-gp4 tool is missing."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/create-fself" || { echo "OpenOrbis create-fself tool is missing."; exit 1; }

$(OBJ_DIR):
	@mkdir -p $@

$(OBJ_DIR)/%.o: src/%.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/miniz_%.o: third_party/miniz/%.c | $(OBJ_DIR)
	$(CC) $(CFLAGS) -Ithird_party/miniz -c -o $@ $<

$(ELF_FILE): $(OBJECTS) | check-toolchain
	@mkdir -p build
	$(LD) $(OBJECTS) -o $@ $(LDFLAGS)
	$(CREATE_FSELF) -in=$@ -out=$(OELF_FILE) --eboot "eboot.bin" --paid 0x3800000000000011

eboot.bin: $(ELF_FILE)
	@test -f $@ || { echo "OpenOrbis did not produce eboot.bin."; exit 1; }

sce_sys/param.sfo: Makefile | check-toolchain
	$(PKG_TOOL) sfo_new $@
	$(PKG_TOOL) sfo_setentry $@ APP_TYPE --type Integer --maxsize 4 --value 1
	$(PKG_TOOL) sfo_setentry $@ APP_VER --type Utf8 --maxsize 8 --value '$(VERSION)'
	$(PKG_TOOL) sfo_setentry $@ ATTRIBUTE --type Integer --maxsize 4 --value 0
	$(PKG_TOOL) sfo_setentry $@ CATEGORY --type Utf8 --maxsize 4 --value 'gd'
	$(PKG_TOOL) sfo_setentry $@ CONTENT_ID --type Utf8 --maxsize 48 --value '$(CONTENT_ID)'
	$(PKG_TOOL) sfo_setentry $@ DOWNLOAD_DATA_SIZE --type Integer --maxsize 4 --value 0
	$(PKG_TOOL) sfo_setentry $@ SYSTEM_VER --type Integer --maxsize 4 --value 0
	$(PKG_TOOL) sfo_setentry $@ TITLE --type Utf8 --maxsize 128 --value '$(TITLE)'
	$(PKG_TOOL) sfo_setentry $@ TITLE_ID --type Utf8 --maxsize 12 --value '$(TITLE_ID)'
	$(PKG_TOOL) sfo_setentry $@ VERSION --type Utf8 --maxsize 8 --value '$(VERSION)'

prepare-runtime: check-toolchain
	@set -e; \
	  sample="$(OO_SDL_SAMPLE)"; \
	  for file in sce_module/libc.prx sce_module/libSceFios2.prx sce_sys/about/right.sprx; do \
	    test -s "$$sample/$$file" || { echo "Missing OpenOrbis SDL2 runtime file: $$sample/$$file"; exit 1; }; \
	  done; \
	  mkdir -p sce_module sce_sys/about; \
	  cp "$$sample/sce_module/libc.prx" sce_module/libc.prx; \
	  cp "$$sample/sce_module/libSceFios2.prx" sce_module/libSceFios2.prx; \
	  cp "$$sample/sce_sys/about/right.sprx" sce_sys/about/right.sprx

$(PKG_FILE): eboot.bin sce_sys/param.sfo sce_sys/icon0.png $(BUNDLED_ADDON_ASSETS) | prepare-runtime
	$(CREATE_GP4) -out pkg.gp4 --content-id=$(CONTENT_ID) --files "$(PKG_CONTENTS)"
	$(PKG_TOOL) pkg_build pkg.gp4 .

host-test: host-test-api host-test-importer host-test-storage

host-test-api:
	@mkdir -p build-host
	$(HOST_CXX) -std=c++17 -Wall -Wextra -Werror -pedantic -D_GNU_SOURCE -Isrc \
		tests/test_api.cpp src/json.cpp src/addonstation_api.cpp src/demo_data.cpp -o build-host/test_api
	./build-host/test_api

build-host/miniz_%.o: third_party/miniz/%.c | build-host
	$(HOST_CC) -std=c99 -O2 -D_GNU_SOURCE -Ithird_party/miniz -c -o $@ $<

build-host:
	@mkdir -p $@

host-test-importer: $(HOST_MINIZ_OBJECTS)
	$(HOST_CXX) -std=c++17 -Wall -Wextra -Werror -pedantic -D_GNU_SOURCE -Isrc -isystem third_party/miniz \
		tests/test_addon_importer.cpp src/addon_importer.cpp src/json.cpp \
		$(HOST_MINIZ_OBJECTS) -o build-host/test_addon_importer
	./build-host/test_addon_importer

host-test-storage:
	@mkdir -p build-host
	$(HOST_CXX) -std=c++17 -Wall -Wextra -Werror -pedantic -D_GNU_SOURCE -Isrc \
		tests/test_minecraft_storage.cpp src/minecraft_storage.cpp -o build-host/test_minecraft_storage
	./build-host/test_minecraft_storage

clean:
	rm -rf build build-host pkg.gp4 eboot.bin $(PKG_FILE) sce_sys/param.sfo
	rm -f $(RUNTIME_FILES)
