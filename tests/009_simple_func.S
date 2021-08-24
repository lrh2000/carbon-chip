.section ".foo"
.global foo
foo:
  li s0, 0xdeadbeef
  jal bar
  li s1, 0xbeefdead
.long 0

bar:
  li a0, 0xfeebdaed
  ret
.long 0
