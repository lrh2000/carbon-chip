#include "signal.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>

static uint8_t signal_stack[SIGSTKSZ];

static void handle_sigill(int sig, void *info, struct target_ucontext *uc)
{
  asm volatile (
      ".option push\n\t"
      ".option norelax\n\t"
      "la gp, __global_pointer$\n\t"
      ".option pop\n\t"
  );

  puts("x0  00000000");
  for (int i = 1; i < 32; ++i)
    printf("x%d%s%08lx\n", i, i <= 9 ? "  " : " ", uc->uc_mcontext.gpr[i - 1]);
  exit(0);
}

int main(void)
{
  struct target_sigaltstack ss;
  memset(&ss, 0, sizeof(ss));
  ss.ss_sp = (long)signal_stack;
  ss.ss_size = SIGSTKSZ;

  if (do_sigaltstack(&ss, NULL) < 0) {
    fprintf(stderr, "do_sigaltstack failed\n");
    return -1;
  }

  struct target_sigaction act;
  memset(&act, 0, sizeof(act));
  act.sa_flags = TARGET_SA_SIGINFO | TARGET_SA_ONSTACK;
  act._sa_handler = (long)&handle_sigill;

  if (do_sigaction(SIGILL, &act, NULL) < 0) {
    fprintf(stderr, "do_sigaction failed\n");
    return -1;
  }

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
      "j foo\n\t"
  );

  __builtin_unreachable();
}
