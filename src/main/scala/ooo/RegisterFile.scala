package pkucs.carbonchip.ooo

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class RegisterFile(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val raddr = Input(Vec(c.NumReadPhyRegs, UInt(c.BitNumPhyRegs.W)))
    val rdata = Output(Vec(c.NumReadPhyRegs, UInt(c.BitNumRegData.W)))

    // Since register x0 is hardwired with all bits equal to 0, do we
    // still need to have a separate write enable signal?
    val wen = Input(Vec(c.NumWritePhyRegs, Bool()))
    val waddr = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumPhyRegs.W)))
    val wdata = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumRegData.W)))
  })

  val regdata = RegInit(
    VecInit((0 until c.NumPhyRegs).map(_.U(c.BitNumRegData.W)))
  )

  val regdataOut = Wire(Vec(c.NumPhyRegs, UInt(c.BitNumRegData.W)))
  for (i <- 0 until c.NumPhyRegs) {
    regdataOut(i) := (if (i == c.PhyRegZeroAddr) c.RegZeroData.U
                      else regdata(i))
  }

  for (i <- 0 until c.NumReadPhyRegs) {
    io.rdata(i) := regdataOut(io.raddr(i))
  }

  for (i <- 0 until c.NumWritePhyRegs) {
    when(io.wen(i)) {
      regdata(io.waddr(i)) := io.wdata(i)
    }
  }
}
