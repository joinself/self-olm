#!/usr/bin/make -f

include common.mk
VERSION := $(MAJOR).$(MINOR).$(PATCH)
PREFIX ?= /usr/local
BUILD_DIR := build
RELEASE_OPTIMIZE_FLAGS ?= -g -O3
DEBUG_OPTIMIZE_FLAGS ?= -g -O0
JS_OPTIMIZE_FLAGS ?= -O3
FUZZING_OPTIMIZE_FLAGS ?= -O3
CC = gcc
EMCC = emcc
AFL_CC = afl-gcc
AFL_CXX = afl-g++
AR = ar

UNAME := $(shell uname)
ifeq ($(UNAME),Darwin)
	SO := dylib
	OLM_LDFLAGS :=
else
	SO := so
	OLM_LDFLAGS := -Wl,-soname,libself_olm.so.$(MAJOR) \
                       -Wl,--version-script,version_script.ver
endif

RELEASE_TARGET := $(BUILD_DIR)/libself_olm.$(SO).$(VERSION)
STATIC_RELEASE_TARGET := $(BUILD_DIR)/libself_olm.a
DEBUG_TARGET := $(BUILD_DIR)/libself_olm_debug.$(SO).$(VERSION)
JS_WASM_TARGET := javascript/olm.js
JS_ASMJS_TARGET := javascript/olm_legacy.js

JS_EXPORTED_FUNCTIONS := javascript/exported_functions.json
JS_EXTRA_EXPORTED_RUNTIME_METHODS := ALLOC_STACK
JS_EXTERNS := javascript/externs.js

PUBLIC_HEADERS := include/self_olm/olm.h include/self_olm/outbound_group_session.h include/self_olm/inbound_group_session.h include/self_olm/pk.h include/self_olm/sas.h

SOURCES := $(wildcard src/*.cpp) $(wildcard src/*.c) \
    lib/crypto-algorithms/sha256.c \
    lib/crypto-algorithms/aes.c \
    lib/curve25519-donna/curve25519-donna.c

FUZZER_SOURCES := $(wildcard fuzzers/fuzz_*.cpp) $(wildcard fuzzers/fuzz_*.c)
TEST_SOURCES := $(wildcard tests/test_*.cpp) $(wildcard tests/test_*.c)

OBJECTS := $(patsubst %.c,%.o,$(patsubst %.cpp,%.o,$(SOURCES)))
RELEASE_OBJECTS := $(addprefix $(BUILD_DIR)/release/,$(OBJECTS))
DEBUG_OBJECTS := $(addprefix $(BUILD_DIR)/debug/,$(OBJECTS))
FUZZER_OBJECTS := $(addprefix $(BUILD_DIR)/fuzzers/objects/,$(OBJECTS))
FUZZER_BINARIES := $(addprefix $(BUILD_DIR)/,$(basename $(FUZZER_SOURCES)))
FUZZER_DEBUG_BINARIES := $(patsubst $(BUILD_DIR)/fuzzers/fuzz_%,$(BUILD_DIR)/fuzzers/debug_%,$(FUZZER_BINARIES))
TEST_BINARIES := $(patsubst tests/%,$(BUILD_DIR)/tests/%,$(basename $(TEST_SOURCES)))
JS_OBJECTS := $(addprefix $(BUILD_DIR)/javascript/,$(OBJECTS))

# pre & post are the js-pre/js-post options to emcc.
# They are injected inside the modularised code and
# processed by the optimiser.
JS_PRE := $(wildcard javascript/*pre.js)
JS_POST := javascript/olm_outbound_group_session.js \
    javascript/olm_inbound_group_session.js \
    javascript/olm_pk.js \
    javascript/olm_sas.js \
    javascript/olm_post.js

# The prefix & suffix are just added onto the start & end
# of what comes out emcc, so are outside of the modularised
# code and not seen by the opimiser.
JS_PREFIX := javascript/olm_prefix.js
JS_SUFFIX := javascript/olm_suffix.js

DOCS := tracing/README.html \
    docs/megolm.html \
    docs/olm.html \
    docs/signing.html \
    README.html \
    CHANGELOG.html

CPPFLAGS += -Iinclude -Ilib \
    -DOLMLIB_VERSION_MAJOR=$(MAJOR) -DOLMLIB_VERSION_MINOR=$(MINOR) \
    -DOLMLIB_VERSION_PATCH=$(PATCH)

# we rely on <stdint.h>, which was introduced in C99
CFLAGS += -Wall -Werror -std=c99
CXXFLAGS += -Wall -Werror -std=c++11
LDFLAGS += -Wall -Werror

CFLAGS_NATIVE = -fPIC
CXXFLAGS_NATIVE = -fPIC

EMCCFLAGS = --closure 1 --memory-init-file 0 -s NO_FILESYSTEM=1 -s INVOKE_RUN=0 -s MODULARIZE=1

# Olm generally doesn't need a lot of memory to encrypt / decrypt its usual
# payloads (ie. Matrix messages), but we do need about 128K of heap to encrypt
# a 64K event (enough to store the ciphertext and the plaintext, bearing in
# mind that the plaintext can only be 48K because base64). We also have about
# 36K of statics. So let's have 256K of memory.
# (This can't be changed by the app with wasm since it's baked into the wasm).
# (emscripten also mandates at least 16MB of memory for asm.js now, so
# we don't use this for the legacy build.)
EMCCFLAGS_WASM += -s TOTAL_STACK=65536 -s TOTAL_MEMORY=262144

EMCCFLAGS_ASMJS += -s WASM=0

EMCC.c = $(EMCC) $(CFLAGS) $(CPPFLAGS) -c
EMCC.cc = $(EMCC) $(CXXFLAGS) $(CPPFLAGS) -c
EMCC_LINK = $(EMCC) $(LDFLAGS) $(EMCCFLAGS)

AFL.c = $(AFL_CC) $(CFLAGS) $(CPPFLAGS) -c
AFL.cc = $(AFL_CXX) $(CXXFLAGS) $(CPPFLAGS) -c
AFL_LINK.c = $(AFL_CC) $(LDFLAGS) $(CFLAGS) $(CPPFLAGS)
AFL_LINK.cc = $(AFL_CXX) $(LDFLAGS) $(CXXFLAGS) $(CPPFLAGS)

# generate .d files when compiling
CPPFLAGS += -MMD

### per-target variables

$(RELEASE_OBJECTS): CFLAGS += $(RELEASE_OPTIMIZE_FLAGS) $(CFLAGS_NATIVE)
$(RELEASE_OBJECTS): CXXFLAGS += $(RELEASE_OPTIMIZE_FLAGS) $(CXXFLAGS_NATIVE)
$(RELEASE_TARGET): LDFLAGS += $(RELEASE_OPTIMIZE_FLAGS)

$(DEBUG_OBJECTS): CFLAGS += $(DEBUG_OPTIMIZE_FLAGS) $(CFLAGS_NATIVE)
$(DEBUG_OBJECTS): CXXFLAGS += $(DEBUG_OPTIMIZE_FLAGS) $(CXXFLAGS_NATIVE)
$(DEBUG_TARGET): LDFLAGS += $(DEBUG_OPTIMIZE_FLAGS)

$(TEST_BINARIES): CPPFLAGS += -Itests/include
$(TEST_BINARIES): LDFLAGS += $(DEBUG_OPTIMIZE_FLAGS) -L$(BUILD_DIR)

$(FUZZER_OBJECTS): CFLAGS += $(FUZZER_OPTIMIZE_FLAGS)
$(FUZZER_OBJECTS): CXXFLAGS += $(FUZZER_OPTIMIZE_FLAGS)
$(FUZZER_BINARIES): CPPFLAGS += -Ifuzzers/include
$(FUZZER_BINARIES): LDFLAGS += $(FUZZER_OPTIMIZE_FLAGS) -L$(BUILD_DIR)
$(FUZZER_DEBUG_BINARIES): CPPFLAGS += -Ifuzzers/include
$(FUZZER_DEBUG_BINARIES): LDFLAGS += $(DEBUG_OPTIMIZE_FLAGS)

$(JS_OBJECTS): CFLAGS += $(JS_OPTIMIZE_FLAGS)
$(JS_OBJECTS): CXXFLAGS += $(JS_OPTIMIZE_FLAGS)
$(JS_WASM_TARGET): LDFLAGS += $(JS_OPTIMIZE_FLAGS)
$(JS_ASMJS_TARGET): LDFLAGS += $(JS_OPTIMIZE_FLAGS)

### Fix to make mkdir work on windows and linux
ifeq ($(shell echo "check_quotes"),"check_quotes")
   WINDOWS := yes
else
   WINDOWS := no
endif

ifeq ($(WINDOWS),yes)
   mkdir = mkdir $(subst /,\,$(1)) > nul 2>&1 || (exit 0)
else
   mkdir = mkdir -p $(1)
endif

### top-level targets

lib: $(RELEASE_TARGET)
.PHONY: lib

$(RELEASE_TARGET): $(RELEASE_OBJECTS)
	$(CXX) $(LDFLAGS) --shared -fPIC \
            $(OLM_LDFLAGS) \
            $(OUTPUT_OPTION) $(RELEASE_OBJECTS)
	ln -sf libself_olm.$(SO).$(VERSION) $(BUILD_DIR)/libself_olm.$(SO).$(MAJOR)
	ln -sf libself_olm.$(SO).$(VERSION) $(BUILD_DIR)/libself_olm.$(SO)

debug: $(DEBUG_TARGET)
.PHONY: debug

$(DEBUG_TARGET): $(DEBUG_OBJECTS)
	$(CXX) $(LDFLAGS) --shared -fPIC \
            $(OLM_LDFLAGS) \
            $(OUTPUT_OPTION) $(DEBUG_OBJECTS)
	ln -sf libself_olm_debug.$(SO).$(VERSION) $(BUILD_DIR)/libself_olm_debug.$(SO).$(MAJOR)

static: $(STATIC_RELEASE_TARGET)
.PHONY: static

$(STATIC_RELEASE_TARGET): $(RELEASE_OBJECTS)
	$(AR) rcs $@ $^

js: $(JS_WASM_TARGET) $(JS_ASMJS_TARGET)
.PHONY: js

# Note that the output file we give to emcc determines the name of the
# wasm file baked into the js, hence messing around outputting to olm.js
# and then renaming it.
$(JS_WASM_TARGET): $(JS_OBJECTS) $(JS_PRE) $(JS_POST) $(JS_EXPORTED_FUNCTIONS) $(JS_PREFIX) $(JS_SUFFIX)
	EMCC_CLOSURE_ARGS="--externs $(JS_EXTERNS)" $(EMCC_LINK) \
	       $(EMCCFLAGS_WASM) \
               $(foreach f,$(JS_PRE),--pre-js $(f)) \
               $(foreach f,$(JS_POST),--post-js $(f)) \
               -s "EXPORTED_FUNCTIONS=@$(JS_EXPORTED_FUNCTIONS)" \
               -s "EXTRA_EXPORTED_RUNTIME_METHODS=$(JS_EXTRA_EXPORTED_RUNTIME_METHODS)" \
               $(JS_OBJECTS) -o $@
	       mv $@ javascript/olmtmp.js
	       cat $(JS_PREFIX) javascript/olmtmp.js $(JS_SUFFIX) > $@
	       rm javascript/olmtmp.js

$(JS_ASMJS_TARGET): $(JS_OBJECTS) $(JS_PRE) $(JS_POST) $(JS_EXPORTED_FUNCTIONS) $(JS_PREFIX) $(JS_SUFFIX)
	EMCC_CLOSURE_ARGS="--externs $(JS_EXTERNS)" $(EMCC_LINK) \
	       $(EMCCFLAGS_ASMJS) \
               $(foreach f,$(JS_PRE),--pre-js $(f)) \
               $(foreach f,$(JS_POST),--post-js $(f)) \
               -s "EXPORTED_FUNCTIONS=@$(JS_EXPORTED_FUNCTIONS)" \
               -s "EXTRA_EXPORTED_RUNTIME_METHODS=$(JS_EXTRA_EXPORTED_RUNTIME_METHODS)" \
               $(JS_OBJECTS) -o $@
	       mv $@ javascript/olmtmp.js
	       cat $(JS_PREFIX) javascript/olmtmp.js $(JS_SUFFIX) > $@
	       rm javascript/olmtmp.js

build_tests: $(TEST_BINARIES)

test: build_tests
	for i in $(TEST_BINARIES); do \
	    echo $$i; \
	    $$i || exit $$?; \
	done

fuzzers: $(FUZZER_BINARIES) $(FUZZER_DEBUG_BINARIES)
.PHONY: fuzzers

$(JS_EXPORTED_FUNCTIONS): $(PUBLIC_HEADERS)
	./exports.py $^ > $@.tmp
	mv $@.tmp $@

all: test js lib debug doc
.PHONY: all

install-headers: $(PUBLIC_HEADERS)
	test -d $(DESTDIR)$(PREFIX)/include/self_olm || $(call mkdir,$(DESTDIR)$(PREFIX)/include/self_olm)
	install -Dm644 $(PUBLIC_HEADERS) $(DESTDIR)$(PREFIX)/include/self_olm/
.PHONY: install-headers

install-debug: debug install-headers
	test -d $(DESTDIR)$(PREFIX)/lib || $(call mkdir,$(DESTDIR)$(PREFIX)/lib)
	install -Dm755 $(DEBUG_TARGET) $(DESTDIR)$(PREFIX)/lib/libself_olm_debug.$(SO).$(VERSION)
	ln -sf libself_olm_debug.$(SO).$(VERSION) $(DESTDIR)$(PREFIX)/lib/libself_olm_debug.$(SO).$(MAJOR)
	ln -sf libself_olm_debug.$(SO).$(VERSION) $(DESTDIR)$(PREFIX)/lib/libself_olm_debug.$(SO)
.PHONY: install-debug

install: lib install-headers
	test -d $(DESTDIR)$(PREFIX)/lib || $(call mkdir,$(DESTDIR)$(PREFIX)/lib)
	install -Dm755 $(RELEASE_TARGET) $(DESTDIR)$(PREFIX)/lib/libself_olm.$(SO).$(VERSION)
	ln -sf libself_olm.$(SO).$(VERSION) $(DESTDIR)$(PREFIX)/lib/libself_olm.$(SO).$(MAJOR)
	ln -sf libself_olm.$(SO).$(VERSION) $(DESTDIR)$(PREFIX)/lib/libself_olm.$(SO)
.PHONY: install

clean:;
	rm -rf $(BUILD_DIR) $(DOCS)
.PHONY: clean

doc: $(DOCS)
.PHONY: doc

### rules for building objects
$(BUILD_DIR)/release/%.o: %.c
	$(call mkdir,$(dir $@))
	$(COMPILE.c) $(OUTPUT_OPTION) $<

$(BUILD_DIR)/release/%.o: %.cpp
	$(call mkdir,$(dir $@))
	$(COMPILE.cc) $(OUTPUT_OPTION) $<

$(BUILD_DIR)/debug/%.o: %.c
	$(call mkdir,$(dir $@))
	$(COMPILE.c) $(OUTPUT_OPTION) $<

$(BUILD_DIR)/debug/%.o: %.cpp
	$(call mkdir,$(dir $@))
	$(COMPILE.cc) $(OUTPUT_OPTION) $<

$(BUILD_DIR)/javascript/%.o: %.c
	$(call mkdir,$(dir $@))
	$(EMCC.c) $(OUTPUT_OPTION) $<

$(BUILD_DIR)/javascript/%.o: %.cpp
	$(call mkdir,$(dir $@))
	$(EMCC.cc) $(OUTPUT_OPTION) $<

$(BUILD_DIR)/tests/%: tests/%.c $(DEBUG_OBJECTS)
	$(call mkdir,$(dir $@))
	$(LINK.c) $< $(DEBUG_OBJECTS) $(LOADLIBES) $(LDLIBS) -o $@

$(BUILD_DIR)/tests/%: tests/%.cpp $(DEBUG_OBJECTS)
	$(call mkdir,$(dir $@))
	$(LINK.cc) $< $(DEBUG_OBJECTS) $(LOADLIBES) $(LDLIBS) -o $@

$(BUILD_DIR)/fuzzers/objects/%.o: %.c
	$(call mkdir,$(dir $@))
	$(AFL.c) $(OUTPUT_OPTION) $<

$(BUILD_DIR)/fuzzers/objects/%.o: %.cpp
	$(call mkdir,$(dir $@))
	$(AFL.cc) $(OUTPUT_OPTION) $<

$(BUILD_DIR)/fuzzers/fuzz_%: fuzzers/fuzz_%.c $(FUZZER_OBJECTS)
	$(AFL_LINK.c) $< $(FUZZER_OBJECTS) $(LOADLIBES) $(LDLIBS) -o $@

$(BUILD_DIR)/fuzzers/fuzz_%: fuzzers/fuzz_%.cpp $(FUZZER_OBJECTS)
	$(AFL_LINK.cc) $< $(FUZZER_OBJECTS) $(LOADLIBES) $(LDLIBS) -o $@

$(BUILD_DIR)/fuzzers/debug_%: fuzzers/fuzz_%.c $(DEBUG_OBJECTS)
	$(LINK.c) $< $(DEBUG_OBJECTS) $(LOADLIBES) $(LDLIBS) -o $@

$(BUILD_DIR)/fuzzers/debug_%: fuzzers/fuzz_%.cpp $(DEBUG_OBJECTS)
	$(LINK.cc) $< $(DEBUG_OBJECTS) $(LOADLIBES) $(LDLIBS) -o $@

%.html: %.rst
	rst2html $< $@

### dependencies

-include $(RELEASE_OBJECTS:.o=.d)
-include $(DEBUG_OBJECTS:.o=.d)
-include $(JS_OBJECTS:.o=.d)
-include $(TEST_BINARIES:=.d)
-include $(FUZZER_OBJECTS:.o=.d)
-include $(FUZZER_BINARIES:=.d)
-include $(FUZZER_DEBUG_BINARIES:=.d)
