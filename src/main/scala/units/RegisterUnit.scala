package pkucs.carbonchip.units

import chisel3._
import chisel3.util.Cat
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.Instruction
import pkucs.carbonchip.instr.AluInstruction
import pkucs.carbonchip.ooo.RegisterFile

class RegisterUnit(implicit c: ChipConfig) extends Module {
  implicit class SeqHelper(val seq: Seq[Bits]) {
    def asUInt(): UInt = VecInit(seq).asUInt()
    def asSInt(): SInt = VecInit(seq).asUInt().asSInt()
  }

  val io = IO(new Bundle {
    val inValid = Input(Vec(c.NumIssueInstrs, Bool()))
    val inInstrs = Input(Vec(c.NumIssueInstrs, Instruction()))

    val aluValid = Output(Vec(c.NumAluInstrs, Bool()))
    val aluInstrs = Output(Vec(c.NumAluInstrs, AluInstruction()))

    val regWriteValid = Input(Vec(c.NumWritePhyRegs, Bool()))
    val regWriteAddr = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumPhyRegs.W)))
    val regWriteData = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumRegData.W)))
  })

  val valid = RegNext(io.inValid, VecInit(Seq.fill(c.NumIssueInstrs)(false.B)))
  for (i <- 0 until c.NumAluInstrs) {
    io.aluValid(i) := valid(i)
  }

  val regFile = Module(new RegisterFile)
  regFile.io.wen := io.regWriteValid
  regFile.io.waddr := io.regWriteAddr
  regFile.io.wdata := io.regWriteData

  val regFwdEna1 = Reg(Vec(c.NumIssueInstrs, Bool()))
  val regFwdAddr1 = Reg(Vec(c.NumIssueInstrs, UInt(c.BitNumPhyRegs.W)))
  val regFwdEna2 = Reg(Vec(c.NumIssueInstrs, Bool()))
  val regFwdAddr2 = Reg(Vec(c.NumIssueInstrs, UInt(c.BitNumPhyRegs.W)))
  for (i <- 0 until c.NumIssueInstrs) {
    regFwdAddr1(i) := io.inInstrs(i).raddr1.asUInt()
    regFwdAddr2(i) := io.inInstrs(i).raddr2.asUInt()
  }

  def withForward(in: SInt, addr: UInt, ena: Bool = true.B): SInt = {
    val out = Wire(SInt(c.BitNumRegData.W))
    out := in
    for (i <- 0 until c.NumWritePhyRegs) {
      when(
        ena && addr =/= c.PhyRegZeroAddr.U
          && io.regWriteValid(i) && io.regWriteAddr(i) === addr
      ) {
        out := io.regWriteData(i).asSInt()
      }
    }
    return out
  }

  for (i <- 0 until c.NumIssueInstrs) {
    for (j <- 0 until c.NumReadRegsPerInstr) {
      regFile.io.raddr(i * c.NumReadRegsPerInstr + j) :=
        io.inInstrs(i).raddr(j).asUInt()
    }
  }

  require(c.NumIssueInstrs == c.NumAluInstrs)
  val aluInstrs = Reg(Vec(c.NumAluInstrs, AluInstruction()))
  for (i <- 0 until c.NumAluInstrs) {
    when(io.inInstrs(i).aluUseImm31) {
      regFwdEna1(i) := false.B
      regFwdEna2(i) := false.B
      aluInstrs(i).inRegData1 := Cat(
        io.inInstrs(i).aluImm31.asUInt(),
        0.U((c.BitNumRegData - 31).W)
      ).asSInt()
      aluInstrs(i).inRegData2 := 0.S
      aluInstrs(i).funct := c.AluFunctAdd.U
    }.otherwise {
      regFwdEna1(i) := true.B
      aluInstrs(i).inRegData1 := withForward(
        regFile.io.rdata(i * c.NumReadRegsPerInstr).asSInt(),
        io.inInstrs(i).raddr1.asUInt()
      )
      when(io.inInstrs(i).aluUseImm12) {
        regFwdEna2(i) := false.B
        aluInstrs(i).inRegData2 := io.inInstrs(i).imm12.asSInt()
      }.otherwise {
        regFwdEna2(i) := true.B
        aluInstrs(i).inRegData2 := withForward(
          regFile.io.rdata(i * c.NumReadRegsPerInstr + 1).asSInt(),
          io.inInstrs(i).raddr2.asUInt()
        )
      }
      aluInstrs(i).funct := io.inInstrs(i).aluFunct.asUInt()
    }
    aluInstrs(i).outRegAddr := io.inInstrs(i).waddr.asUInt()
  }

  io.aluInstrs := aluInstrs
  for (i <- 0 until c.NumAluInstrs) {
    io.aluInstrs(i).inRegData1 := withForward(
      aluInstrs(i).inRegData1,
      regFwdAddr1(i),
      regFwdEna1(i)
    )
    io.aluInstrs(i).inRegData2 := withForward(
      aluInstrs(i).inRegData2,
      regFwdAddr2(i),
      regFwdEna2(i)
    )
  }
}
