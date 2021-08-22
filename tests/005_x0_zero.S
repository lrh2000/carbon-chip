.section ".foo"
.global foo
foo:
  addi x0, x0, 1234
  ori x0, x0, 1111
  xor x3, x3, x0
  sub x4, x0, x4
  slt x5, x6, x0
  slt x6, x0, x6
  sltu x7, x0, x0
  slti x8, x0, 0
.long 0
