#include "defs.h"

extern char stack[], stack_top[];

extern void foo(void);

static void dump_register(int addr, uint32_t data)
{
  static char str[] = "x0  00000000\n";

  if (addr <= 9) {
    str[1] = addr + '0';
    str[2] = ' ';
  } else {
    str[1] = addr / 10 + '0';
    str[2] = addr % 10 + '0';
  }

  for (int i = 0; i < 8; ++i)
  {
    int num = (data >> (i * 4)) & 0xF;
    char c;
    if (num <= 9)
      c = '0' + num;
    else
      c = 'a' + (num - 10);
    str[11 - i] = c;
  }

  if (do_write(STDOUT_FILENO, str, sizeof(str) - 1) != sizeof(str) - 1)
    do_exit(-3);
}

// The `target_ucontext` structures can be different in different versions of QEMU,
// for example they are different in QEMU 4.2.1 and QEMU 6.0.0, which will lead to
// different offsets of their member `uc_mcontext` (that contains register contents
// we are interested in).
// So we have to dynamically search the `uc_mcontext` field, instead of using a fixed
// value as the offset.
static int search_mcontext(void *uc)
{
  uint32_t *start = (uint32_t *)uc;
  uint32_t *now = start;

  for (;;)
  {
    for (int i = 1; i < 32; ++i)
      if (now[i] != i)
        goto next;
    break;
next:
    ++now;
  }

  return now - start;
}

static void handle_sigill(int sig, void *info, void *uc)
{
  static int mc_offset = 0;

  uint32_t *mc; // [pc, x1, ..., x31] = mc[0, 1, ..., 31]

  if (!mc_offset) {
    mc_offset = search_mcontext(uc);
    mc = (uint32_t *)uc + mc_offset;
    mc[0] = (uint32_t)&foo;
    return;
  }
  mc = (uint32_t *)uc + mc_offset;

  dump_register(0, 0);
  for (int i = 1; i < 32; ++i)
    dump_register(i, mc[i]);

  do_exit(0);
}

int main(void)
{
  struct target_sigaltstack ss;
  memset(&ss, 0, sizeof(ss));
  ss.ss_sp = (long)stack;
  ss.ss_size = stack_top - stack;

  if (do_sigaltstack(&ss, NULL) < 0)
    do_exit(-1);

  struct target_sigaction act;
  memset(&act, 0, sizeof(act));
  act.sa_flags = TARGET_SA_SIGINFO | TARGET_SA_ONSTACK;
  act._sa_handler = (long)&handle_sigill;

  if (do_sigaction(TARGET_SIGILL, &act, NULL) < 0)
    do_exit(-2);

  asm volatile (
      "li x0, 0\n\t"
      "li x1, 1\n\t"
      "li x2, 2\n\t"
      "li x3, 3\n\t"
      "li x4, 4\n\t"
      "li x5, 5\n\t"
      "li x6, 6\n\t"
      "li x7, 7\n\t"
      "li x8, 8\n\t"
      "li x9, 9\n\t"
      "li x10, 10\n\t"
      "li x11, 11\n\t"
      "li x12, 12\n\t"
      "li x13, 13\n\t"
      "li x14, 14\n\t"
      "li x15, 15\n\t"
      "li x16, 16\n\t"
      "li x17, 17\n\t"
      "li x18, 18\n\t"
      "li x19, 19\n\t"
      "li x20, 20\n\t"
      "li x21, 21\n\t"
      "li x22, 22\n\t"
      "li x23, 23\n\t"
      "li x24, 24\n\t"
      "li x25, 25\n\t"
      "li x26, 26\n\t"
      "li x27, 27\n\t"
      "li x28, 28\n\t"
      "li x29, 29\n\t"
      "li x30, 30\n\t"
      "li x31, 31\n\t"
      ".long 0\n\t"
  );

  __builtin_unreachable();
}
