package pkucs.carbonchip.units

import chisel3._
import chisel3.util.{switch, is}
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.AluInstruction

class ArithmeticLogicUnit(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val instr = Input(AluInstruction())
    val valid = Input(Bool())

    val outRegData = Output(UInt(c.BitNumRegData.W))
    val outRegAddr = Output(UInt(c.BitNumPhyRegs.W))
    val outValid = Output(Bool())
  })

  val valid = RegNext(io.valid, false.B)
  io.outValid := valid

  val regAddr = RegNext(io.instr.outRegAddr)
  io.outRegAddr := regAddr

  val data1 = io.instr.inRegData1
  val data2 = io.instr.inRegData2

  val add = data1 + data2
  val sub = data1 - data2
  val sll = data1 << data2(c.NumAluShamtBits - 1, 0)
  val slt = Mux(data1 < data2, 1.S, 0.S)
  val sltu = Mux(data1.asUInt() < data2.asUInt(), 1.S, 0.S)
  val xor = data1 ^ data2
  val srl = data1.asUInt() >> data2(c.NumAluShamtBits - 1, 0)
  val sra = data1 >> data2(c.NumAluShamtBits - 1, 0)
  val or = data1 | data2
  val and = data1 & data2

  val result = Reg(UInt(c.BitNumRegData.W))
  io.outRegData := result

  result := DontCare
  @annotation.nowarn("msg=discarded non-Unit value")
  val _ = switch(io.instr.funct) {
    is(c.AluFunctAdd.U) {
      result := add.asUInt()
    }
    is(c.AluFunctSub.U) {
      result := sub.asUInt()
    }
    is(c.AluFunctSll.U) {
      result := sll.asUInt()
    }
    is(c.AluFunctSlt.U) {
      result := slt.asUInt()
    }
    is(c.AluFunctSltu.U) {
      result := sltu.asUInt()
    }
    is(c.AluFunctXor.U) {
      result := xor.asUInt()
    }
    is(c.AluFunctSrl.U) {
      result := srl.asUInt()
    }
    is(c.AluFunctSra.U) {
      result := sra.asUInt()
    }
    is(c.AluFunctOr.U) {
      result := or.asUInt()
    }
    is(c.AluFunctAnd.U) {
      result := and.asUInt()
    }
  }
}
