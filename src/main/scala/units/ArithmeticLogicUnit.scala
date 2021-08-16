package pkucs.carbonchip.units

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class ArithmeticLogicUnit(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val operands = Input(Vec(c.NumReadRegsPerInstr, UInt(c.BitNumRegData.W)))
    val result = Output(UInt(c.BitNumRegData.W))

    val inValid = Input(Bool())
    val inRegAddr = Input(UInt(c.BitNumPhyRegs.W))

    val outValid = Output(Bool())
    val outRegAddr = Output(UInt(c.BitNumPhyRegs.W))
  })

  val valid = RegNext(io.inValid, false.B)
  io.outValid := valid

  val regAddr = RegNext(io.inRegAddr)
  io.outRegAddr := regAddr

  require(c.NumReadRegsPerInstr == 2)

  val add = Wire(UInt(c.BitNumRegData.W))
  add := io.operands(0) + io.operands(1)

  val result = Reg(UInt(c.BitNumRegData.W))
  result := add
  io.result := result
}
