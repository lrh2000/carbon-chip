package pkucs.carbonchip.units

import chisel3._
import chisel3.util.Cat
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.Instruction
import pkucs.carbonchip.instr.AluInstruction
import pkucs.carbonchip.instr.BruInstruction
import pkucs.carbonchip.ooo.RegisterFile

class RegisterUnit(implicit c: ChipConfig) extends Module {
  implicit class SeqHelper(val seq: Seq[Bits]) {
    def asUInt(): UInt = VecInit(seq).asUInt()
    def asSInt(): SInt = VecInit(seq).asUInt().asSInt()
  }

  val io = IO(new Bundle {
    val inValid = Input(Vec(c.NumIssueInstrs, Bool()))
    val inInstrs = Input(Vec(c.NumIssueInstrs, Instruction()))
    val inBranch = Input(Bool())

    val aluValid = Output(Vec(c.NumAluInstrs, Bool()))
    val aluInstrs = Output(Vec(c.NumAluInstrs, AluInstruction()))

    val bruValid = Output(Bool())
    val bruInstr = Output(BruInstruction())

    val regWriteValid = Input(Vec(c.NumWritePhyRegs, Bool()))
    val regWriteAddr = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumPhyRegs.W)))
    val regWriteData = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumRegData.W)))

    val branchFail = Input(Bool())
  })

  val aluValid = RegInit(VecInit(Seq.fill(c.NumIssueInstrs)(false.B)))
  for (i <- 0 until c.NumAluInstrs) {
    io.aluValid(i) := aluValid(i)
  }

  val bruValid = RegInit(false.B)
  io.bruValid := bruValid && !io.branchFail

  require(c.NumAluInstrs == c.NumIssueInstrs)
  for (i <- 0 until c.NumIssueInstrs - 1) {
    aluValid(i) := io.inValid(i)
  }
  when(io.inBranch) {
    aluValid(c.NumAluInstrs - 1) := false.B
    bruValid := io.inValid(c.NumIssueInstrs - 1)
  }.otherwise {
    aluValid(c.NumAluInstrs - 1) := io.inValid(c.NumIssueInstrs - 1)
    bruValid := false.B
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

  def withForward(in: SInt, addr: UInt): SInt = {
    val out = Wire(SInt(c.BitNumRegData.W))
    out := in
    for (i <- 0 until c.NumWritePhyRegs) {
      when(
        addr =/= c.PhyRegZeroAddr.U
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

  val regData1 = Reg(Vec(c.NumIssueInstrs, SInt(c.BitNumRegData.W)))
  val regData2 = Reg(Vec(c.NumIssueInstrs, SInt(c.BitNumRegData.W)))
  for (i <- 0 until c.NumIssueInstrs) {
    regData1(i) := withForward(
      regFile.io.rdata(i * c.NumReadRegsPerInstr).asSInt(),
      io.inInstrs(i).raddr1.asUInt()
    )
    regData2(i) := withForward(
      regFile.io.rdata(i * c.NumReadRegsPerInstr + 1).asSInt(),
      io.inInstrs(i).raddr2.asUInt()
    )
  }

  val regFwdData1 = Wire(Vec(c.NumIssueInstrs, SInt(c.BitNumRegData.W)))
  val regFwdData2 = Wire(Vec(c.NumIssueInstrs, SInt(c.BitNumRegData.W)))
  for (i <- 0 until c.NumIssueInstrs) {
    regFwdData1(i) := withForward(regData1(i), regFwdAddr1(i))
    regFwdData2(i) := withForward(regData2(i), regFwdAddr2(i))
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
      aluInstrs(i).setPc := false.B
    }.otherwise {
      regFwdEna1(i) := true.B
      aluInstrs(i).inRegData1 := DontCare
      when(io.inInstrs(i).aluUseImm12) {
        regFwdEna2(i) := false.B
        aluInstrs(i).inRegData2 := io.inInstrs(i).imm12.asSInt()
      }.otherwise {
        regFwdEna2(i) := true.B
        aluInstrs(i).inRegData2 := DontCare
      }
      aluInstrs(i).funct := io.inInstrs(i).aluFunct.asUInt()
      aluInstrs(i).setPc := io.inInstrs(i).aluSetPc
    }
    aluInstrs(i).outRegAddr := io.inInstrs(i).waddr.asUInt()
  }

  io.aluInstrs := aluInstrs
  for (i <- 0 until c.NumAluInstrs) {
    io.aluInstrs(i).inRegData1 :=
      Mux(regFwdEna1(i), regFwdData1(i), aluInstrs(i).inRegData1)
    io.aluInstrs(i).inRegData2 :=
      Mux(regFwdEna2(i), regFwdData2(i), aluInstrs(i).inRegData2)
  }

  val branchFunct3 = Reg(UInt(3.W))
  branchFunct3 := io.inInstrs(c.NumIssueInstrs - 1).funct3.asUInt()

  io.bruInstr.funct3 := branchFunct3
  io.bruInstr.inData1 := regFwdData1(c.NumIssueInstrs - 1)
  io.bruInstr.inData2 := regFwdData2(c.NumIssueInstrs - 1)
}
