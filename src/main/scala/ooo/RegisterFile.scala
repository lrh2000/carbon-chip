package pkucs.carbonchip.ooo

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class RegisterFile(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val raddr = Input(Vec(c.NumReadPhyRegs, UInt(c.BitNumPhyRegs.W)))
    val rdata = Output(Vec(c.NumReadPhyRegs, UInt(c.BitNumRegData.W)))

    val wen = Input(Vec(c.NumWritePhyRegs, Bool()))
    val waddr = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumPhyRegs.W)))
    val wdata = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumRegData.W)))
  })

  val regdata = RegInit(
    VecInit((0 until c.NumPhyRegs).map(_.U(c.BitNumRegData.W)))
  )

  for (i <- 0 until c.NumReadPhyRegs) {
    io.rdata(i) := regdata(io.raddr(i))
  }

  for (i <- 0 until c.NumWritePhyRegs) {
    when(io.wen(i)) {
      regdata(io.waddr(i)) := io.wdata(i)
    }
  }
}
