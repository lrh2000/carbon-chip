package pkucs.carbonchip.units

import chisel3._
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.IssuedInstr
import pkucs.carbonchip.instr.InstrWithData
import pkucs.carbonchip.ooo.RegisterFile

class RegisterUnit(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val inValid = Input(Vec(c.NumIssueInstrs, Bool()))
    val inInstrs = Input(Vec(c.NumIssueInstrs, IssuedInstr()))

    val outValid = Output(Vec(c.NumIssueInstrs, Bool()))
    val outInstrs = Output(Vec(c.NumIssueInstrs, InstrWithData()))

    val regWriteValid = Input(Vec(c.NumWritePhyRegs, Bool()))
    val regWriteAddr = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumPhyRegs.W)))
    val regWriteData = Input(Vec(c.NumWritePhyRegs, UInt(c.BitNumRegData.W)))
  })

  val valid = RegNext(io.inValid, VecInit(Seq.fill(c.NumIssueInstrs)(false.B)))
  io.outValid := valid

  val regFile = Module(new RegisterFile)
  regFile.io.wen := io.regWriteValid
  regFile.io.waddr := io.regWriteAddr
  regFile.io.wdata := io.regWriteData

  val instrs = Reg(Vec(c.NumIssueInstrs, InstrWithData()))
  val lastInstrs = RegNext(io.inInstrs)
  for (i <- 0 until c.NumIssueInstrs) {
    for (j <- 0 until c.NumReadRegsPerInstr) {
      regFile.io
        .raddr(i * c.NumReadRegsPerInstr + j) := io.inInstrs(i).inRegAddr(j)
      instrs(i).inRegData(j) := regFile.io.rdata(i * c.NumReadRegsPerInstr + j)
      for (k <- 0 until c.NumWritePhyRegs) {
        when(
          io.regWriteValid(k)
            && io.regWriteAddr(k) === io.inInstrs(i).inRegAddr(j)
        ) {
          instrs(i).inRegData(j) := io.regWriteData(k)
        }
      }
    }
    instrs(i).outRegAddr := io.inInstrs(i).outRegAddr

    io.outInstrs(i).outRegAddr := instrs(i).outRegAddr
    for (j <- 0 until c.NumReadRegsPerInstr) {
      io.outInstrs(i).inRegData(j) := instrs(i).inRegData(j)
      for (k <- 0 until c.NumWritePhyRegs) {
        when(
          io.regWriteValid(k)
            && io.regWriteAddr(k) === lastInstrs(i).inRegAddr(j)
        ) {
          io.outInstrs(i).inRegData(j) := io.regWriteData(k)
        }
      }
    }
  }
}
