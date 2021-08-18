.text
.global foo
foo:
  li x31, 0x805deee5
  li x30, 0x5522e42d
  addi x29, x30, 166
  addi x28, x31, -321
  slli x27, x30, 3
  slli x26, x31, 3
  srli x25, x30, 4
  srli x24, x31, 4
  srai x23, x30, 5
  srai x22, x31, 5
  xori x21, x30, 271
  andi x20, x31, 938
  ori x19, x30, 763
  slti x18, x31, 0
  sltiu x17, x31, 0
.long 0
