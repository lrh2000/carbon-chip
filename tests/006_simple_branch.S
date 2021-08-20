.section ".foo"
.global foo
foo:
  li x1, 1234
  li x2, 4321
  add x3, x1, x2
  li x4, 5555
  beq x3, x4, .succ

.fail:
  li x31, 0xdeadbeef
.long 0

.succ:
  li x31, 0xbeefdead
.long 0
