#pragma once

// Reference:
//   qemu/v6.0.0/source/linux-user/generic/signal.h
//   qemu/v6.0.0/source/linux-user/syscall_defs.h
//   qemu/v6.0.0/source/linux-user/riscv/target_signal.h
//   qemu/v6.0.0/source/linux-user/riscv/syscall32_nr.h
//   qemu/v6.0.0/source/linux-user/riscv/signal.c

typedef int abi_int;
typedef unsigned long abi_ulong;

typedef unsigned int uint32_t;
typedef long ssize_t;
typedef unsigned long size_t;

#define NULL  ((void *)0)

#define STDIN_FILENO   0
#define STDOUT_FILENO  1
#define STDERR_FILENO  2

#define TARGET_ABI_BITS    (sizeof(long) * 8)
#define TARGET_NSIG        64
#define TARGET_NSIG_BPW    TARGET_ABI_BITS
#define TARGET_NSIG_WORDS  (TARGET_NSIG / TARGET_NSIG_BPW)

#define TARGET_NR_write         64
#define TARGET_NR_exit          93
#define TARGET_NR_sigaltstack   132
#define TARGET_NR_rt_sigaction  134

typedef struct {
    abi_ulong sig[TARGET_NSIG_WORDS];
} target_sigset_t;

typedef struct target_sigaltstack {
    abi_ulong ss_sp;
    abi_int ss_flags;
    abi_ulong ss_size;
} target_stack_t;

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

#define TARGET_SIGILL    4

#define TARGET_SA_SIGINFO  0x00000004
#define TARGET_SA_ONSTACK  0x08000000

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

static inline ssize_t
do_write(int fildes, const void *buf, size_t nbyte)
{
  register long a0 asm("a0") = (long)fildes;
  register long a1 asm("a1") = (long)buf;
  register long a2 asm("a2") = (long)nbyte;
  register long a7 asm("a7") = (long)TARGET_NR_write;

  asm volatile (
      "ecall"
      : "+r" (a0)
      : "r" (a0), "r" (a1), "r" (a2), "r" (a7)
  );

  return a0;
}

static inline _Noreturn int
do_exit(int status)
{
  register long a0 asm("a0") = (long)status;
  register long a7 asm("a7") = (long)TARGET_NR_exit;

  asm volatile (
      "ecall"
      : "+r" (a0)
      : "r" (a0), "r" (a7)
  );

  __builtin_unreachable();
}

static inline void *
memset(void *s, int c, size_t n)
{
  char *p = (char *)s;
  while (n--)
    *p++ = c;
  return s;
}
