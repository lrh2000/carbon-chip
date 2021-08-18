package pkucs.carbonchip.instr

import chisel3._
import chisel3.util.{Cat, switch, is}
import pkucs.carbonchip.config.ChipConfig

class InstrDecoder(implicit c: ChipConfig) extends Module {
  implicit class SeqHelper(val seq: Seq[Bits]) {
    def :=(other: Seq[Bits]): Unit = {
      seq.zip(other).foreach { case (a, b) => a := b }
    }
  }

  val io = IO(new Bundle {
    val pc = Input(UInt(c.NumProgCounterBits.W))
    val raw = Input(UInt(c.NumInstrBits.W))

    val valid = Output(Bool())
    val cooked = Output(Instruction())

    val regReadEna = Output(Vec(c.NumReadRegsPerInstr, Bool()))
    val isaRegRead = Output(Vec(c.NumReadRegsPerInstr, UInt(c.BitNumIsaRegs.W)))
    val phyRegRead = Input(Vec(c.NumReadRegsPerInstr, UInt(c.BitNumPhyRegs.W)))

    val regWriteEna = Output(Vec(c.NumWriteRegsPerInstr, Bool()))
    val isaRegWrite =
      Output(Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumIsaRegs.W)))
    val phyRegWrNew =
      Input(Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumPhyRegs.W)))
    val phyRegWrOld =
      Input(Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumPhyRegs.W)))
  })

  val opcode = io.raw(6, 0)
  val imm12 = io.raw(31, 20)
  val rd = io.raw(11, 7)
  val rs1 = io.raw(19, 15)
  val rs2 = io.raw(24, 20)
  val funct3 = io.raw(14, 12)
  val funct7 = io.raw(31, 25)
  val imm20 = io.raw(31, 12)

  require(c.NumReadRegsPerInstr == 2)
  require(c.NumWriteRegsPerInstr == 1)
  require(c.BitNumIsaRegs == 5)
  io.isaRegRead(0) := rs1
  io.isaRegRead(1) := rs2
  io.isaRegWrite(0) := rd
  val raddr1 = io.phyRegRead(0)
  val raddr2 = io.phyRegRead(1)
  val waddr = io.phyRegWrNew(0)
  val caddr = io.phyRegWrOld(0)

  val ren1 = Wire(Bool())
  val ren2 = Wire(Bool())
  val wen = Wire(Bool())
  io.regReadEna(0) := ren1
  io.regReadEna(1) := ren2
  io.regWriteEna(0) := wen

  val out = Wire(Instruction())
  io.cooked := out

  out := DontCare
  ren1 := false.B
  ren2 := false.B
  wen := false.B
  io.valid := false.B

  @annotation.nowarn("msg=discarded non-Unit value")
  val _ = switch(opcode) {
    is(c.OpcodeAluReg.U) {
      ren1 := true.B
      ren2 := true.B
      out.raddr1 := raddr1.asBools()
      out.raddr2 := raddr2.asBools()

      wen := true.B
      out.waddr := waddr.asBools()
      out.caddr := caddr.asBools()

      out.aluUseImm31 := false.B
      out.aluUseImm12 := false.B
      when(
        funct3 === c.Funct3AddSub.U || funct3 === c.Funct3SrlSra.U
      ) {
        out.aluFunct := Cat(funct7(5), funct3).asBools()
        io.valid := Cat(funct7(6), funct7(4, 0)) === 0.U
      }.otherwise {
        out.aluFunct := Cat(0.U(1.W), funct3).asBools()
        io.valid := funct7 === 0.U
      }
    }
    is(c.OpcodeAluImm.U) {
      ren1 := true.B
      ren2 := false.B
      out.raddr1 := raddr1.asBools()
      out.imm12 := imm12.asBools()

      wen := true.B
      out.waddr := waddr.asBools()
      out.caddr := caddr.asBools()

      out.aluUseImm31 := false.B
      out.aluUseImm12 := true.B
      when(funct3 === c.Funct3SrlSra.U) {
        out.aluFunct := Cat(funct7(5), funct3).asBools()
        io.valid := Cat(funct7(6), funct7(4, 0)) === 0.U
      }.otherwise {
        out.aluFunct := Cat(0.U(1.W), funct3).asBools()
        io.valid := funct3 =/= c.Funct3Sll.U || funct7 === 0.U
      }
    }
    is(c.OpcodeLui.U) {
      ren1 := false.B
      ren2 := false.B
      out.aluImm31 := Cat(imm20, 0.U(11.W)).asBools()

      wen := true.B
      out.waddr := waddr.asBools()
      out.caddr := caddr.asBools()

      out.aluUseImm31 := true.B
      io.valid := true.B
    }
    is(c.OpcodeAuipc.U) {
      ren1 := false.B
      ren2 := false.B
      out.aluImm31 := (
        Cat(imm20, 0.U(11.W))
          + Cat(io.pc, 0.U((31 - c.NumProgCounterBits).W))
      ).asBools()

      wen := true.B
      out.waddr := waddr.asBools()
      out.caddr := caddr.asBools()

      out.aluUseImm31 := true.B
      io.valid := true.B
    }
  }
}
