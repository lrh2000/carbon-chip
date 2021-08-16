package pkucs.carbonchip.instr

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class InstrWithData(implicit c: ChipConfig) extends Bundle {
  val inRegData = Vec(c.NumReadRegsPerInstr, UInt(c.BitNumRegData.W))
  val outRegAddr = Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumPhyRegs.W))
}

object InstrWithData {
  def apply()(implicit c: ChipConfig) = new InstrWithData
}
