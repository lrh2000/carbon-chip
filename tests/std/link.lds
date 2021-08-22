OUTPUT_FORMAT("elf32-littleriscv", "elf32-littleriscv",
              "elf32-littleriscv")
OUTPUT_ARCH(riscv)
ENTRY(_start)

SECTIONS
{
  . = 0x80000000;
  .text : { *(.foo) *(.text*) }

  . = ALIGN(8);
  .data : { *(.data*) }

  . = ALIGN(8);
  .rodata : { *(.rodata*) }

  . = ALIGN(8);
  .bss : { *(.bss*) }

  /DISCARD/ : { *(*) }
}
