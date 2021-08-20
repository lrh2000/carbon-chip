.section ".foo"
.global foo
foo:
  li x1, 0
  li x2, 100
.L1:
  add x1, x1, x2
  addi x2, x2, -1
  bnez x2, .L1
.long 0
