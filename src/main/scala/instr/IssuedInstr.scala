package pkucs.carbonchip.instr

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class IssuedInstr(implicit c: ChipConfig) extends Bundle {
  val inRegAddr = Vec(c.NumReadRegsPerInstr, UInt(c.BitNumPhyRegs.W))
  val outRegAddr = Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumPhyRegs.W))
}

object IssuedInstr {
  def apply()(implicit c: ChipConfig) = new IssuedInstr
}
