# Bedrock Skins -- offline port
#
#   make test                      # validate + runtime smoke test of the built BP
#   make build                     # fetch the Java source if needed, then build
#   make build SOURCE=~/src/Bedrock-Skins-master
#   make all                       # build + validate
#
# The Java mod source is not vendored (it is not ours to ship), so `make`
# downloads it on demand through the GitHub blob API the first time it is used.

SOURCE       ?= source/Bedrock-Skins-master
OUT          ?= dist
PY           ?= python3
# Match this to the client build you install on (PS4/Android/Windows).
GAME_VERSION ?= 1.21.0

.PHONY: all build skins addon cosmetics validate test source clean

all: build validate

# --------------------------------------------------------------------- source
source:
	@if [ ! -d "$(SOURCE)" ]; then \
		echo "[make] $(SOURCE) not found -> tools/fetch_source.sh"; \
		./tools/fetch_source.sh source; \
	fi

# ---------------------------------------------------------------------- build
build: source
	$(PY) tools/build_addon.py --source "$(SOURCE)" --out "$(OUT)" \
		--game-version "$(GAME_VERSION)"

skins: source
	$(PY) tools/build_addon.py --source "$(SOURCE)" --out "$(OUT)" \
		--game-version "$(GAME_VERSION)" --only skins

addon: source
	$(PY) tools/build_addon.py --source "$(SOURCE)" --out "$(OUT)" \
		--game-version "$(GAME_VERSION)" --only addon

cosmetics: source
	$(PY) tools/build_addon.py --source "$(SOURCE)" --out "$(OUT)" \
		--game-version "$(GAME_VERSION)" \
		--only skins --cosmetics --cosmetics-combos 3 --combo-limit 9

# ----------------------------------------------------------------------- test
validate:
	$(PY) tools/validate.py "$(OUT)"

test: validate
	node tools/test_main_js.mjs "$(OUT)/BedrockSkins_BP/scripts/main.js"

clean:
	rm -rf "$(OUT)"
	find . -name __pycache__ -type d -prune -exec rm -rf {} +
