# pkg addon test — Standalone PS4 Minecraft Addon Installer Test PKG (PrizmaVisuals 1.3.10)
TITLE       := pkg addon test
VERSION     := 0.1
TITLE_ID    := ATST00001
CONTENT_ID  := IV0000-ATST00001_00-PKGADDONTEST0000

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
ELF_FILE    := build/pkg-addon-test.elf
OELF_FILE   := build/pkg-addon-test.oelf
RAW_PKG     := $(CONTENT_ID).pkg
PKG_FILE    := pkg-addon-test.pkg
OO_SDL_SAMPLE := $(TOOLCHAIN)/samples/SDL2

RUNTIME_FILES := sce_module/libc.prx sce_module/libSceFios2.prx sce_sys/about/right.sprx
BUNDLED_ADDON_ASSETS := assets/PrizmaVisuals-1.3.10.mcpack assets/PrizmaVisuals-cover.bmp assets/PrizmaVisuals-cover.png
PKG_CONTENTS := eboot.bin sce_sys/param.sfo sce_sys/icon0.png $(RUNTIME_FILES) $(BUNDLED_ADDON_ASSETS)

CXX_OBJECTS := $(OBJ_DIR)/main.o $(OBJ_DIR)/installer.o $(OBJ_DIR)/json.o
MINIZ_SOURCES := ../ps4/third_party/miniz/miniz.c ../ps4/third_party/miniz/miniz_zip.c \
                 ../ps4/third_party/miniz/miniz_tinfl.c ../ps4/third_party/miniz/miniz_tdef.c
MINIZ_OBJECTS := $(OBJ_DIR)/miniz_miniz.o $(OBJ_DIR)/miniz_miniz_zip.o \
                 $(OBJ_DIR)/miniz_miniz_tinfl.o $(OBJ_DIR)/miniz_miniz_tdef.o
OBJECTS     := $(CXX_OBJECTS) $(MINIZ_OBJECTS)

HOST_MINIZ_OBJECTS := build-host/miniz_miniz.o build-host/miniz_miniz_zip.o \
                      build-host/miniz_miniz_tinfl.o build-host/miniz_miniz_tdef.o

LIBS        := -lc -lkernel -lc++ -lSceUserService -lSceVideoOut -lSceAudioOut \
               -lScePad -lSceSysmodule -lSDL2
COMMONFLAGS := --target=x86_64-pc-freebsd12-elf -fPIC -funwind-tables \
               -isysroot $(TOOLCHAIN) -isystem $(TOOLCHAIN)/include
CXXFLAGS    := $(COMMONFLAGS) -std=c++17 -fexceptions -D_POSIX_C_SOURCE=200809L -D_GNU_SOURCE \
               -isystem $(TOOLCHAIN)/include/c++/v1 -Isrc -I../ps4/src -I../ps4/third_party/miniz
CFLAGS      := $(COMMONFLAGS) -std=c99 -fPIC -D_POSIX_C_SOURCE=200809L -D_GNU_SOURCE \
               -I../ps4/third_party/miniz
LDFLAGS     := -m elf_x86_64 -pie --script $(TOOLCHAIN)/link.x --eh-frame-hdr \
               -L$(TOOLCHAIN)/lib $(LIBS) $(TOOLCHAIN)/lib/crt1.o

.PHONY: all pkg clean check-toolchain prepare-runtime host-test objects
all: pkg

objects: $(OBJECTS)

pkg: check-toolchain $(PKG_FILE)

check-toolchain:
	@test -n "$(TOOLCHAIN)" || { echo "Set OO_PS4_TOOLCHAIN to your OpenOrbis toolchain directory."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/PkgTool.Core" || { echo "OpenOrbis package tools not found under $(TOOLCHAIN)/bin/$(TOOL_OS)."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/create-gp4" || { echo "OpenOrbis create-gp4 tool is missing."; exit 1; }
	@test -x "$(TOOLCHAIN)/bin/$(TOOL_OS)/create-fself" || { echo "OpenOrbis create-fself tool is missing."; exit 1; }

$(OBJ_DIR):
	@mkdir -p $@

$(OBJ_DIR)/main.o: src/main.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/installer.o: src/installer.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/json.o: ../ps4/src/json.cpp | $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c -o $@ $<

$(OBJ_DIR)/miniz_%.o: ../ps4/third_party/miniz/%.c | $(OBJ_DIR)
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
	  cp "$$sample/sce_sys/about/right.sprx" sce_sys/about/right.sprx; \
	  cp "../ps4/sce_sys/icon0.png" sce_sys/icon0.png

$(PKG_FILE): eboot.bin sce_sys/param.sfo $(BUNDLED_ADDON_ASSETS) | prepare-runtime
	$(CREATE_GP4) -out pkg.gp4 --content-id=$(CONTENT_ID) --files "$(PKG_CONTENTS)"
	$(PKG_TOOL) pkg_build pkg.gp4 .
	cp "$(RAW_PKG)" "$(PKG_FILE)"

build-host:
	@mkdir -p $@

build-host/miniz_%.o: ../ps4/third_party/miniz/%.c | build-host
	$(HOST_CC) -std=c99 -O2 -D_GNU_SOURCE -I../ps4/third_party/miniz -c -o $@ $<

host-test: $(HOST_MINIZ_OBJECTS)
	$(HOST_CXX) -std=c++17 -Wall -Wextra -Werror -pedantic -D_GNU_SOURCE \
		-Isrc -I../ps4/src -isystem ../ps4/third_party/miniz \
		tests/test_installer.cpp src/installer.cpp ../ps4/src/json.cpp \
		$(HOST_MINIZ_OBJECTS) -o build-host/test_installer
	./build-host/test_installer

clean:
	rm -rf build build-host pkg.gp4 eboot.bin $(RAW_PKG) $(PKG_FILE) sce_sys sce_module
