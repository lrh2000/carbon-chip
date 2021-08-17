.text
.global foo
foo:
  li x1, 0x3080d6d2
  li x2, 0x995c5ed7
  li x3, 10
  xor x4, x1, x2
  and x5, x2, x1
  or x6, x1, x2
  add x7, x2, x1
  sub x8, x1, x2
  slt x9, x1, x2
  sltu x10, x1, x2
  sll x11, x1, x3
  sll x12, x2, x3
  srl x13, x1, x3
  srl x14, x2, x3
  sra x15, x1, x3
  sra x16, x2, x3
.long 0
