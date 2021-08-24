.section ".foo"
.global foo
foo:
  li t0, 0xdeadbeef
  auipc ra, 0
  addi ra, ra, 7
  beqz x0, bar
  xor t4, t0, t2
.long 0

bar:
  xor t1, t0, ra
  la t2, 0xbeafdead
  jalr t3, 5(ra)
.long 0
