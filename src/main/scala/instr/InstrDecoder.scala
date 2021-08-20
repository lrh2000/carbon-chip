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
    val meta = Output(InstructionMeta())

    val isaRegRead = Output(Vec(c.NumReadRegsPerInstr, UInt(c.BitNumIsaRegs.W)))
    val phyRegRead = Input(Vec(c.NumReadRegsPerInstr, UInt(c.BitNumPhyRegs.W)))

    val regWriteEna = Output(Vec(c.NumWriteRegsPerInstr, Bool()))
    val isaRegWrite =
      Output(Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumIsaRegs.W)))
    val phyRegWrNew =
      Input(Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumPhyRegs.W)))
    val phyRegWrOld =
      Input(Vec(c.NumWriteRegsPerInstr, UInt(c.BitNumPhyRegs.W)))

    val isJump = Output(Bool())
    val jumpPc = Output(UInt(c.NumProgCounterBits.W))

    val isBranch = Output(Bool())
    val branchPc = Output(UInt(c.NumProgCounterBits.W))
  })

  val opcode = io.raw(6, 0)
  val imm12 = io.raw(31, 20)
  val rd = io.raw(11, 7)
  val rs1 = io.raw(19, 15)
  val rs2 = io.raw(24, 20)
  val funct3 = io.raw(14, 12)
  val funct7 = io.raw(31, 25)
  val imm20 = io.raw(31, 12)

  // It actually contains only 11 bits, because we always ignore
  // the lowest bit as we do not support compressed instructions
  // (C extension) now.
  val imm12b = Cat(
    io.raw(31),
    io.raw(7),
    io.raw(30, 25),
    io.raw(11, 9)
  )
  val btarget = io.pc.asSInt() + imm12b.asSInt()
  val nextpc = io.pc + 1.U

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
  io.meta.ready(0) := !ren1
  io.meta.ready(1) := !ren2
  io.regWriteEna(0) := wen

  val out = Wire(Instruction())
  io.cooked := out

  out := DontCare
  ren1 := false.B
  ren2 := false.B
  wen := false.B
  io.valid := false.B

  io.isJump := false.B
  io.jumpPc := DontCare
  io.isBranch := false.B
  io.branchPc := DontCare
  io.meta.alu := DontCare

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
      }.otherwise {
        out.aluFunct := Cat(0.U(1.W), funct3).asBools()
      }
      io.valid := true.B
      io.meta.alu := true.B
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
      }.otherwise {
        out.aluFunct := Cat(0.U(1.W), funct3).asBools()
      }
      io.valid := true.B
      io.meta.alu := true.B
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
      io.meta.alu := true.B
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
      io.meta.alu := true.B
    }
    is(c.OpcodeBranch.U) {
      ren1 := true.B
      ren2 := true.B
      out.raddr1 := raddr1.asBools()
      out.raddr2 := raddr2.asBools()

      wen := false.B

      // Simple static branch predictor:
      // Backward branches will be taken and forward branches will not.
      when(imm12b.asSInt() < 0.S) {
        io.isJump := true.B
        io.jumpPc := btarget.asUInt()
        io.isBranch := true.B
        io.branchPc := nextpc
        out.funct3 := funct3.asBools()
      }.otherwise {
        io.isBranch := true.B
        io.branchPc := btarget.asUInt()
        out.funct3 := (funct3 ^ c.Funct3BxxNegator.U).asBools()
      }

      io.valid := true.B
      io.meta.alu := false.B
    }
  }
}
