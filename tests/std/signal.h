#pragma once

#include <stdint.h>

#define QEMU_ALIGNED(X) __attribute__((aligned(X)))

typedef int32_t abi_int;

#define TARGET_ABI_BITS 32
typedef int32_t abi_long;
typedef uint32_t abi_ulong;

#define TARGET_NSIG	   64
#define TARGET_NSIG_BPW	   TARGET_ABI_BITS
#define TARGET_NSIG_WORDS  (TARGET_NSIG / TARGET_NSIG_BPW)

#define TARGET_NR_sigaltstack 132
#define TARGET_NR_rt_sigaction 134

typedef struct {
    abi_ulong sig[TARGET_NSIG_WORDS];
} target_sigset_t;

typedef struct target_sigaltstack {
    abi_ulong ss_sp;
    abi_int ss_flags;
    abi_ulong ss_size;
} target_stack_t;

struct target_sigcontext {
    abi_long pc;
    abi_long gpr[31]; /* x0 is not present, so all offsets must be -1 */
    uint64_t fpr[32];
    uint32_t fcsr;
}; /* cf. riscv-linux:arch/riscv/include/uapi/asm/ptrace.h */

struct target_ucontext {
    unsigned long uc_flags;
    struct target_ucontext *uc_link;
    target_stack_t uc_stack;
    target_sigset_t uc_sigmask;
    uint8_t   __unused[1024 / 8 - sizeof(target_sigset_t)];
    struct target_sigcontext uc_mcontext QEMU_ALIGNED(16);
};

struct target_sigaction {
        abi_ulong _sa_handler;
        abi_ulong sa_flags;
#ifdef TARGET_ARCH_HAS_SA_RESTORER
        abi_ulong sa_restorer;
#endif
        target_sigset_t sa_mask;
#ifdef TARGET_ARCH_HAS_KA_RESTORER
        abi_ulong ka_restorer;
#endif
};

#define TARGET_SA_SIGINFO	0x00000004
#define TARGET_SA_ONSTACK	0x08000000

static inline int
do_sigaction(int sig,
             const struct target_sigaction *act,
             struct target_sigaction *old_act)
{
  register long a0 asm("a0") = (long)sig;
  register long a1 asm("a1") = (long)act;
  register long a2 asm("a2") = (long)old_act;
  register long a3 asm("a3") = (long)sizeof(target_sigset_t);
  register long a7 asm("a7") = (long)TARGET_NR_rt_sigaction;

  asm volatile (
      "ecall"
      : "+r" (a0)
      : "r" (a0), "r" (a1), "r" (a2), "r" (a3), "r" (a7)
  );

  return a0;
}

static inline int
do_sigaltstack(const struct target_sigaltstack *ss,
               struct target_sigaltstack *old_ss)
{
  register long a0 asm("a0") = (long)ss;
  register long a1 asm("a1") = (long)old_ss;
  register long a7 asm("a7") = (long)TARGET_NR_sigaltstack;

  asm volatile (
      "ecall"
      : "+r" (a0)
      : "r" (a0), "r" (a1), "r" (a7)
  );

  return a0;
}
