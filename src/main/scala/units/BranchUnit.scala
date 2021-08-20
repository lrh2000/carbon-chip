package pkucs.carbonchip.units

import chisel3._
import chisel3.util.{switch, is}
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.BruInstruction

class BranchUnit(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val instr = Input(BruInstruction())
    val valid = Input(Bool())

    val branchSucc = Output(Bool())
    val branchFail = Output(Bool())
  })

  val succ = RegInit(false.B)
  val fail = RegInit(false.B)
  io.branchSucc := succ
  io.branchFail := fail

  val out = Wire(Bool())
  when(io.valid && !fail) {
    succ := out
    fail := !out
  }.otherwise {
    succ := false.B
    fail := false.B
  }

  val data1 = io.instr.inData1
  val data2 = io.instr.inData2

  val equal = data1 === data2
  val lt = data1 < data2
  val ltu = data1.asUInt() < data2.asUInt()

  out := DontCare
  @annotation.nowarn("msg=discarded non-Unit value")
  val _ = switch(io.instr.funct3) {
    is(c.Funct3Beq.U) {
      out := equal
    }
    is(c.Funct3Bne.U) {
      out := !equal
    }
    is(c.Funct3Blt.U) {
      out := lt
    }
    is(c.Funct3Bge.U) {
      out := !lt
    }
    is(c.Funct3Bltu.U) {
      out := ltu
    }
    is(c.Funct3Bgeu.U) {
      out := !ltu
    }
  }
}
