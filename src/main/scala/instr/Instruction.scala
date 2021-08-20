package pkucs.carbonchip.instr

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class Instruction(implicit c: ChipConfig) extends Bundle {
  val bits = Vec(44, Bool())

  require(c.BitNumPhyRegs == 6)
  def raddr1 = bits.slice(0, 6)
  def raddr2 = bits.slice(6, 12)
  def waddr = bits.slice(31, 37)
  def caddr = bits.slice(37, 43)
  def imm12 = bits.slice(12, 24)
  def funct3 = bits.slice(25, 28)

  def aluImm31 = bits.slice(13, 31) ++ bits.slice(0, 13)
  def aluFunct = bits.slice(24, 28)
  def aluUseImm31 = bits(43)
  def aluUseImm12 = bits(29)

  def raddr(i: Integer): Seq[Bits] = {
    require(i == 0 || i == 1)
    return bits.slice(i * 6, (i + 1) * 6)
  }
}

object Instruction {
  def apply()(implicit c: ChipConfig) = new Instruction
}
