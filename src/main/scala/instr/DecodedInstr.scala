package pkucs.carbonchip.instr

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class DecodedInstr(implicit c: ChipConfig) extends Bundle {
  val readyFlags = Vec(c.NumReadRegsPerInstr, Bool())
  val inRegAddr = Vec(c.NumReadRegsPerInstr, UInt(c.BitNumPhyRegs.W))
  val outRegAddr = Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumPhyRegs.W))
  val killedRegAddr = Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumPhyRegs.W))
}

object DecodedInstr {
  def apply()(implicit c: ChipConfig) = new DecodedInstr
}
