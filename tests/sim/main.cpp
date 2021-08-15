#include <verilated.h>
#include <verilated_vcd_c.h>
#include <verilated_vpi.h>
#include "VCarbonChip.h"

#define VPI_GET_HANDLE(name)                                    \
  ({                                                            \
    const char *_n = (name);                                    \
    vpiHandle _vh = vpi_handle_by_name((PLI_BYTE8 *)_n, NULL);  \
    if (_vh == NULL)                                            \
      vl_fatal(__FILE__, __LINE__, __func__,                    \
          ("handle not found " + std::string(_n)).c_str());     \
    _vh;                                                        \
  })

#define VPI_GET_DATA(handle)                                    \
  ({                                                            \
    vpiHandle _h = (handle);                                    \
    s_vpi_value _v;                                             \
    _v.format = vpiIntVal;                                      \
    vpi_get_value(_h, &_v);                                     \
    _v.value.integer;                                           \
  })

#define VPI_READ_DATA(name)                                     \
  VPI_GET_DATA(VPI_GET_HANDLE(name))

static void dump_registers()
{
  const char *const mapping = "TOP.CarbonChip.decode.mapTable.mapping_";
  const char *const regdata = "TOP.CarbonChip.regrw.regFile.regdata_";

  for (int i = 0; i < 32; ++i)
  {
    std::string name;
    int index = VPI_READ_DATA(
        (name = mapping + std::to_string(i)).c_str());
    uint32_t data = VPI_READ_DATA(
        (name = regdata + std::to_string(index)).c_str());
    printf("x%d%s%08x\n", i, i <= 9 ? "  " : " ", data);
  }
}

int main(int argc, char **argv)
{
  Verilated::commandArgs(argc, argv);
  VerilatedContext *contextp = new VerilatedContext;
  VCarbonChip *chip = new VCarbonChip(contextp);
  VerilatedVcdC *tfp = new VerilatedVcdC;

  Verilated::traceEverOn(true);
  chip->trace(tfp, 99);
  tfp->open(argc >= 1 ? argv[argc - 1] : "trace.vcd");

  int halt_time = 0;
  chip->reset = 1;
  do {
    if ((contextp->time() & 15) == 0)
      chip->clock = 0;
    if ((contextp->time() & 15) == 8)
      chip->clock = 1;
    if (contextp->time() == 32)
      chip->reset = 0;

    if (chip->io_halt)
      halt_time += 1;

    contextp->timeInc(1);
    chip->eval();
    tfp->dump(contextp->time());
  } while (halt_time <= 25 * 32);
  // Wait 25 more cycles to make sure that all
  // oustanding instrutions have retired (FIXME).

  dump_registers();

  chip->final();
  tfp->close();
  delete chip;
  delete tfp;

  return 0;
}
