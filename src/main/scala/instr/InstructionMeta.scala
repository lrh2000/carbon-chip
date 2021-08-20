package pkucs.carbonchip.instr

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class InstructionMeta(implicit c: ChipConfig) extends Bundle {
  val ready = Vec(c.NumReadRegsPerInstr, Bool())
  val alu = Bool()
}

object InstructionMeta {
  def apply()(implicit c: ChipConfig) = new InstructionMeta()
}
