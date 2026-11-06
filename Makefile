# Host requirements: Docker (with compose plugin), Git, adb, make.
# Everything Gradle-related runs inside the builder container.

export HOST_UID := $(shell id -u)
export HOST_GID := $(shell id -g)

COMPOSE := docker compose
RUN     := $(COMPOSE) run --rm
APK     := app/build/outputs/apk/debug/app-debug.apk

.PHONY: help image build test lint apk install bench probe inspect clean shell

help: ## List targets
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-10s %s\n", $$1, $$2}'

image: ## Build the Docker builder image
	$(COMPOSE) build build

build: ## Compile all modules (debug sources + unit test sources)
	$(RUN) build

test: ## Run JVM unit tests
	$(RUN) test

lint: ## Run Android lint (fails on errors)
	$(RUN) lint

apk: ## Assemble the debug APK -> app/build/outputs/apk/debug/app-debug.apk
	$(RUN) assemble-debug
	@ls -l $(APK)

install: ## Install the debug APK on the connected device (host adb)
	@command -v adb >/dev/null || { echo "adb not found on host"; exit 1; }
	@[ -f $(APK) ] || { echo "$(APK) missing: run 'make apk' first"; exit 1; }
	@[ "$$(adb get-state 2>/dev/null)" = "device" ] || { echo "No device in 'device' state (adb devices)"; exit 1; }
	adb install -r $(APK)
	adb shell am start -n dev.audiogarage/.MainActivity

bench: ## Extractor benchmark harness (available from step 0.10)
	@echo "make bench: not implemented yet (roadmap step 0.10)"; exit 1

probe: ## Run the JVM probe tool against YouTube, e.g. make probe ARGS="sample <playlistUrl>"
	@[ -n "$(ARGS)" ] || { echo 'Usage: make probe ARGS="sample <url> | fetch <outDir> <videoId>..."'; exit 2; }
	$(RUN) build ./gradlew --no-daemon -q :tools:probe:run --args="$(ARGS)"

inspect: ## ffprobe every .m4a in DIR (codec, duration, bitrate, fragmented MP4 or not), e.g. make inspect DIR=build/probe
	@[ -n "$(DIR)" ] || { echo "Usage: make inspect DIR=<dir>"; exit 2; }
	$(RUN) --entrypoint bash build docker/inspect-m4a.sh "$(DIR)"

shell: ## Open a shell in the builder container
	$(RUN) --entrypoint bash build

clean: ## Remove build outputs (keeps the Gradle cache volume)
	$(RUN) --entrypoint ./gradlew build --no-daemon clean
