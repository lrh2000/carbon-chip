package pkucs.carbonchip.instr

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class BruInstruction(implicit c: ChipConfig) extends Bundle {
  val inData1 = SInt(c.BitNumRegData.W)
  val inData2 = SInt(c.BitNumRegData.W)
  val funct3 = UInt(3.W)
}

object BruInstruction {
  def apply()(implicit c: ChipConfig) = new BruInstruction()
}
