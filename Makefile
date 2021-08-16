OUT     := out
SOURCES := $(shell find src/main/ -name "*.scala")
TARGET  := $(OUT)/CarbonChip.v

.PHONY:
verilog: $(TARGET)

$(TARGET): $(SOURCES)
	sbt "run -td $(OUT)"
	sed -i "s/\\(\\] \\(regdata\\|mapping\\)_[0-9]*\\);/\\1 \\/*verilator public*\\/;/" $(TARGET)

SIM_OUT     := $(OUT)/sim
SIM_SOURCES := tests/sim/main.cpp
SIM_TARGET  := $(SIM_OUT)/VCarbonChip

.PHONY:
sim: $(SIM_TARGET)

$(SIM_TARGET): $(TARGET) $(SIM_SOURCES)
	verilator --cc -O3 $(OUT)/*.v ../$(SIM_SOURCES) -Mdir $(SIM_OUT) --trace --vpi --exe
	$(MAKE) -j -C $(SIM_OUT) -f VCarbonChip.mk VCarbonChip

TEST_DIR := tests
TEST_OUT := $(OUT)/$(TEST_DIR)
TEST_ASMS := $(wildcard $(TEST_DIR)/*.S)
TEST_DIFFS := $(TEST_ASMS:%.S=$(OUT)/%.diff)
TEST_STDOBJ := $(TEST_OUT)/std-main.o $(TEST_OUT)/std-start.o

CROSS   ?= riscv64-unknown-elf-
CC      := $(CROSS)gcc
OBJCOPY := $(CROSS)objcopy
CFLAGS  := -mabi=ilp32 -march=rv32i -Wall -Werror -O3 -nostdlib -nostartfiles
LDFLAGS := -Wl,-lgcc -Wl,--no-relax
QEMU    := qemu-riscv32

.PHONY:
test: $(TEST_DIFFS)

$(TEST_OUT):
	mkdir -p $@

$(TEST_OUT)/%.o: $(TEST_DIR)/%.S | $(TEST_OUT)
	$(CC) -c $(CFLAGS) -o $@ $<

$(TEST_OUT)/std-%.o: $(TEST_DIR)/std/%.c | $(TEST_OUT)
	$(CC) -c $(CFLAGS) -o $@ $<

$(TEST_OUT)/std-%.o: $(TEST_DIR)/std/%.S | $(TEST_OUT)
	$(CC) -c $(CFLAGS) -o $@ $<

$(TEST_OUT)/%.bin: $(TEST_OUT)/%.o
	$(OBJCOPY) -O binary -j .text $< $@

$(TEST_OUT)/%.dat: $(TEST_OUT)/%.bin
	xxd -e -g 8 -c 8 $< | awk '{ print $$2 }' > $@

$(TEST_OUT)/%: $(TEST_OUT)/%.o $(TEST_STDOBJ)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

$(TEST_OUT)/%.ans: $(TEST_OUT)/%
	$(QEMU) $< > $@

$(TEST_OUT)/%.out2: $(TEST_OUT)/%.dat $(SIM_TARGET)
	cp $< /tmp/inst.dat
	$(SIM_TARGET) $(TEST_OUT)/$*.trace > $@

.PHONY:
$(TEST_OUT)/%.diff: $(TEST_OUT)/%.ans $(TEST_OUT)/%.out2
	diff $^

.SECONDARY:
