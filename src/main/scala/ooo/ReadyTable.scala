package pkucs.carbonchip.ooo

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class ReadyTable(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val phyRegRead = Input(Vec(c.NumReadIsaRegs, UInt(c.BitNumPhyRegs.W)))
    val phyRegFlag = Output(Vec(c.NumReadIsaRegs, Bool()))

    val phyRegFree = Input(Vec(c.NumWriteIsaRegs, UInt(c.BitNumPhyRegs.W)))

    val regReadyFlag = Input(Vec(c.NumReadyRegs, Bool()))
    val regReadyAddr = Input(Vec(c.NumReadyRegs, UInt(c.BitNumPhyRegs.W)))
  })

  val flags = Wire(Vec(c.NumPhyRegs, Bool()))
  val flagsReg = RegNext(flags, VecInit(Seq.fill(c.NumPhyRegs)(true.B)))
  flags := flagsReg

  for (i <- 0 until c.NumReadyRegs) {
    when(io.regReadyFlag(i)) {
      flags(io.regReadyAddr(i)) := true.B
    }
  }

  for (i <- 0 until c.NumWriteIsaRegs) {
    flags(io.phyRegFree(i)) := false.B
  }

  flags(c.PhyRegZeroAddr) := true.B

  for (i <- 0 until c.NumReadIsaRegs) {
    io.phyRegFlag(i) := flags(io.phyRegRead(i))
  }
}
