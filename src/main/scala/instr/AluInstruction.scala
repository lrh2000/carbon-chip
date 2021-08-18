package pkucs.carbonchip.instr

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class AluInstruction(implicit c: ChipConfig) extends Bundle {
  val inRegData1 = SInt(c.BitNumRegData.W)
  val inRegData2 = SInt(c.BitNumRegData.W)
  val outRegAddr = UInt(c.BitNumRegData.W)
  val funct = UInt(c.BitNumAluFunct.W)
}

object AluInstruction {
  def apply()(implicit c: ChipConfig) = new AluInstruction
}
