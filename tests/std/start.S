.text
.global _start
_start:
  la t0, handle_exp
  csrw mtvec, t0

  li x0, 0
  li x1, 1
  li x2, 2
  li x3, 3
  li x4, 4
  li x5, 5
  li x6, 6
  li x7, 7
  li x8, 8
  li x9, 9
  li x10, 10
  li x11, 11
  li x12, 12
  li x13, 13
  li x14, 14
  li x15, 15
  li x16, 16
  li x17, 17
  li x18, 18
  li x19, 19
  li x20, 20
  li x21, 21
  li x22, 22
  li x23, 23
  li x24, 24
  li x25, 25
  li x26, 26
  li x27, 27
  li x28, 28
  li x29, 29
  li x30, 30
  li x31, 31

  j foo

handle_exp:
  csrw mscratch, x31
  la x31, regs

  sw x0, 0(x31)
  sw x1, 4(x31)
  sw x2, 8(x31)
  sw x3, 12(x31)
  sw x4, 16(x31)
  sw x5, 20(x31)
  sw x6, 24(x31)
  sw x7, 28(x31)
  sw x8, 32(x31)
  sw x9, 36(x31)
  sw x10, 40(x31)
  sw x11, 44(x31)
  sw x12, 48(x31)
  sw x13, 52(x31)
  sw x14, 56(x31)
  sw x15, 60(x31)
  sw x16, 64(x31)
  sw x17, 68(x31)
  sw x18, 72(x31)
  sw x19, 76(x31)
  sw x20, 80(x31)
  sw x21, 84(x31)
  sw x22, 88(x31)
  sw x23, 92(x31)
  sw x24, 96(x31)
  sw x25, 100(x31)
  sw x26, 104(x31)
  sw x27, 108(x31)
  sw x28, 112(x31)
  sw x29, 116(x31)
  sw x30, 120(x31)

  csrr x30, mscratch
  sw x30, 124(x31)

  la a0, regs
  la sp, stack_top
  j finish

.bss
stack:
  .skip 8192
stack_top:

regs:
  .skip 4 * 32
