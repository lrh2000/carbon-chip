OUT     := out
SOURCES := $(shell find src/main/ -name "*.scala")
TARGET  := $(OUT)/CarbonChip.v $(OUT)/CarbonChip.FetchUnit.instrMem.v

verilog: $(TARGET)

$(TARGET): $(SOURCES)
	sbt "run -td $(OUT)"
	sed -i "s/\\(\\] \\(regdata\\|mapping\\)_[0-9]*\\);/\\1 \\/*verilator public*\\/;/" $(TARGET)

SIM_OUT     := $(OUT)/sim
SIM_SOURCES := tests/sim/main.cpp
SIM_TARGET  := $(SIM_OUT)/VCarbonChip

sim: $(SIM_TARGET)

$(SIM_TARGET): $(TARGET) $(SIM_SOURCES)
	verilator --cc $(TARGET) ../$(SIM_SOURCES) -Mdir $(SIM_OUT) --trace --vpi --exe --build

TEST_DIR := tests
TEST_OUT := $(OUT)/$(TEST_DIR)
TEST_ASMS := $(shell find $(TEST_DIR) -name "*.S")
TEST_DIFFS := $(TEST_ASMS:%.S=$(OUT)/%.diff)
TEST_STDSRC := $(TEST_DIR)/std/main.c
TEST_STDOBJ := $(TEST_OUT)/libstd.o

CC      := riscv64-elf-gcc
OBJCOPY := riscv64-elf-objcopy
CFLAGS  := -mabi=ilp32 -march=rv32i -Wall -Werror -O3
QEMU    := qemu-riscv32

test: $(TEST_DIFFS)

$(TEST_OUT):
	mkdir -p $@

$(TEST_OUT)/%.o: $(TEST_DIR)/%.S | $(TEST_OUT)
	$(CC) -c $(CFLAGS) -o $@ $<

$(TEST_OUT)/%.bin: $(TEST_OUT)/%.o
	$(OBJCOPY) -O binary -j .text $< $@

$(TEST_OUT)/%.dat: $(TEST_OUT)/%.bin
	xxd -e -g 8 -c 8 $< | awk '{ print $$2 }' > $@

$(TEST_OUT)/%: $(TEST_OUT)/%.o $(TEST_STDOBJ)
	$(CC) $(CFLAGS) -o $@ $^

$(TEST_OUT)/%.ans: $(TEST_OUT)/%
	$(QEMU) $< > $@

$(TEST_OUT)/%.out2: $(TEST_OUT)/%.dat $(SIM_TARGET)
	cp $< /tmp/inst.dat
	$(SIM_TARGET) $(TEST_OUT)/$*.trace > $@

$(TEST_OUT)/%.diff: $(TEST_OUT)/%.ans $(TEST_OUT)/%.out2
	diff $^ | tee $@

$(TEST_STDOBJ): $(TEST_STDSRC) | $(TEST_OUT)
	$(CC) -c $(CFLAGS) -o $@ $<

.SECONDARY:
