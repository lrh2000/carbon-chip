static volatile unsigned int *const
  mmio_uart_out = (volatile unsigned int *)0x10000000;
static volatile unsigned int *const
  mmio_powerctl = (volatile unsigned int *)0x00100000;

#define UART_BUSY 0x80000000u
#define POWER_OFF 0x00005555u

static inline void uart_putc(char c)
{
  while (*mmio_uart_out & UART_BUSY);
  *mmio_uart_out = c;
}

static inline _Noreturn void poweroff(void)
{
  for (;;)
    *mmio_powerctl = POWER_OFF;
}

static inline void uart_puts(const char *s)
{
  while (*s)
    uart_putc(*s++);
  uart_putc('\n');
}

static void dump_register(int addr, unsigned long data)
{
  static char str[] = "x0  00000000";

  if (addr <= 9) {
    str[1] = addr + '0';
    str[2] = ' ';
  } else {
    str[1] = addr / 10 + '0';
    str[2] = addr % 10 + '0';
  }

  for (unsigned int i = 0;
       i < sizeof(unsigned long) * 2;
       ++i)
  {
    int num = (data >> (i * 4)) & 0xF;
    char c;
    if (num <= 9)
      c = '0' + num;
    else
      c = 'a' + (num - 10);
    str[11 - i] = c;
  }

  uart_puts(str);
}

_Noreturn void finish(unsigned long regs[])
{
  for (int i = 0; i < 32; ++i)
    dump_register(i, regs[i]);

  poweroff();
  __builtin_unreachable();
}
