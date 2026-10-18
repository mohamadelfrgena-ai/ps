# pkg addon test — PS4 Design UI + Dynamic Addon/World Installer (content from GitHub Releases)
TITLE       := pkg addon test
# AddonStation PS4 Mini-App — v0.22 test build (UI v2 redesign)
VERSION     := 0.22
TITLE_ID    := ATST00002
CONTENT_ID  := IV0000-ATST00002_00-PKGADDONTEST0000

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

PKG_TOOL    := $(TOOLCHAIN)/bin/$(TOOL_OS)/PkgTool.Core
CREATE_FSELF:= $(TOOLCHAIN)/bin/$(TOOL_OS)/create-fself
CREATE_GP4  := $(TOOLCHAIN)/bin/$(TOOL_OS)/create-gp4
OBJ_DIR     := build/obj
ELF_FILE    := build/pkg-addon-test.elf
OELF_FILE   := build/pkg-addon-test.oelf
RAW_PKG     := $(CONTENT_ID).pkg
PKG_FILE    := pkg-addon-test.pkg
OO_SDL_SAMPLE := $(TOOLCHAIN)/samples/SDL2

PKG_FILES   := eboot.bin \
               sce_sys/param.sfo \
               sce_sys/icon0.png \
               sce_sys/about/right.sprx \
               sce_module/libc.prx \
               sce_module/libSceFios2.prx \
               assets/ui.pak

OBJECTS     := $(OBJ_DIR)/main.o $(OBJ_DIR)/installer.o $(OBJ_DIR)/http_download.o \
               $(OBJ_DIR)/supabase_client.o \
               $(OBJ_DIR)/ime_dialog.o $(OBJ_DIR)/ui_engine.o $(OBJ_DIR)/screens.o $(OBJ_DIR)/jbc.o

LIBS        := -lc -lkernel -lc++ -lSceUserService -lSceVideoOut -lSceAudioOut \
               -lScePad -lSceSysmodule -lSDL2 -lSceNet -lSceSsl -lSceHttp -lSceImeDialog
COMMONFLAGS := --target=x86_64-pc-freebsd12-elf -fPIC -funwind-tables \
               -isysroot $(TOOLCHAIN) -isystem $(TOOLCHAIN)/include
CFLAGS      := $(COMMONFLAGS) -std=c11 -mno-red-zone -D__ORBIS__ -Isrc
CXXFLAGS    := $(COMMONFLAGS) -std=c++17 -fexceptions -D_POSIX_C_SOURCE=200809L -D_GNU_SOURCE -D__ORBIS__ \
               -Werror=switch \
               -isystem $(TOOLCHAIN)/include/c++/v1 -Isrc
LDFLAGS     := -m elf_x86_64 -pie --script $(TOOLCHAIN)/link.x --eh-frame-hdr \
               -L$(TOOLCHAIN)/lib $(LIBS) $(TOOLCHAIN)/lib/crt1.o

.PHONY: all pkg clean check-toolchain prepare-runtime host-test objects
all: pkg

objects: $(OBJECTS)

pkg: check-toolchain $(PKG_FILE)

check-toolchain:
	@test -n "$(TOOLCHAIN)" || { echo "Set OO_PS4_TOOLCHAIN to your OpenOrbis toolchain directory."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/PkgTool.Core" || { echo "OpenOrbis package tools not found under $(TOOLCHAIN)/bin/$(TOOL_OS)."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/create-fself" || { echo "OpenOrbis create-fself tool is missing."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/create-gp4" || { echo "OpenOrbis create-gp4 tool is missing."; exit 1; }

$(OBJ_DIR):
	@mkdir -p $@

$(OBJ_DIR)/main.o: src/main.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/installer.o: src/installer.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/ui_engine.o: src/ui_engine.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/screens.o: src/screens.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/http_download.o: src/http_download.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/supabase_client.o: src/supabase_client.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/ime_dialog.o: src/ime_dialog.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/jbc.o: src/jbc.c | $(OBJ_DIR)
	$(CC) $(CFLAGS) -c -o $@ $<

$(ELF_FILE): $(OBJECTS) | check-toolchain
	@mkdir -p build
	$(LD) $(OBJECTS) -o $@ $(LDFLAGS)
	$(CREATE_FSELF) -in=$@ -out=$(OELF_FILE) --eboot "eboot.bin" --paid 0x3800000000000011

eboot.bin: $(ELF_FILE)
	@test -f $@ || { echo "OpenOrbis did not produce eboot.bin."; exit 1; }

sce_sys/param.sfo: Makefile | check-toolchain
	@mkdir -p sce_sys
	$(PKG_TOOL) sfo_new $@
	$(PKG_TOOL) sfo_setentry $@ APP_TYPE --type Integer --maxsize 4 --value 0
	$(PKG_TOOL) sfo_setentry $@ APP_VER --type Utf8 --maxsize 8 --value '$(VERSION)'
	$(PKG_TOOL) sfo_setentry $@ ATTRIBUTE --type Integer --maxsize 4 --value 32
	$(PKG_TOOL) sfo_setentry $@ CATEGORY --type Utf8 --maxsize 4 --value 'gde'
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
	  cp "$$sample/sce_sys/about/right.sprx" sce_sys/about/right.sprx; \
	  cp "../ps4/sce_sys/icon0.png" sce_sys/icon0.png

assets/ui.pak: tools/build_ui_pak.py assets/font_cjk.ttf
	python3 tools/build_ui_pak.py assets/ui.pak

$(PKG_FILE): eboot.bin sce_sys/param.sfo assets/ui.pak | prepare-runtime
	$(CREATE_GP4) -out pkg.gp4 --content-id=$(CONTENT_ID) --files="$(PKG_FILES)"
	$(PKG_TOOL) pkg_build pkg.gp4 .
	cp "$(RAW_PKG)" "$(PKG_FILE)"

host-test:
	@mkdir -p build-host
	$(HOST_CXX) -std=c++17 -Wall -Wextra -Werror -pedantic -D_GNU_SOURCE -Isrc \
		tests/test_installer.cpp src/installer.cpp src/supabase_client.cpp src/http_download.cpp \
		-o build-host/test_installer
	./build-host/test_installer

clean:
	rm -rf build build-host pkg.gp4 eboot.bin $(RAW_PKG) $(PKG_FILE) sce_sys sce_module assets/folder.raw
