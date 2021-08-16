.text
.global _start

_start:
  la sp, stack_top

  call main
loop:
  j loop

.bss
.global stack
.global stack_top

stack:
  .skip 8192
stack_top:
