package pkucs.carbonchip

import chisel3._
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.units.FetchUnit
import pkucs.carbonchip.units.DecodeUnit
import pkucs.carbonchip.ooo.ReorderBuffer
import pkucs.carbonchip.units.RegisterUnit
import pkucs.carbonchip.units.ArithmeticLogicUnit
import pkucs.carbonchip.units.BranchUnit

class CarbonChip(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val halt = Output(Bool())
    val unused = Output(Vec(c.NumWritePhyRegs, UInt(c.BitNumRegData.W)))
  })

  val fetch = Module(new FetchUnit)
  val decode = Module(new DecodeUnit)
  val reorder = Module(new ReorderBuffer)
  val regrw = Module(new RegisterUnit)
  val alu0 = Module(new ArithmeticLogicUnit)
  val alu1 = Module(new ArithmeticLogicUnit)
  val bru = Module(new BranchUnit)

  fetch.io.fetchAddrIn := decode.io.fetchAddrOut

  decode.io.fetchAddrIn := fetch.io.fetchAddrOut
  decode.io.fetchValid := fetch.io.fetchValid
  decode.io.fetchInstrs := fetch.io.fetchInstrs
  decode.io.decodeReady := reorder.io.decodeReady
  decode.io.regCommitEna := reorder.io.commitValid
  decode.io.phyRegCommit := reorder.io.commitRegs
  decode.io.regReadyFlag := reorder.io.regReadyValid
  decode.io.regReadyAddr := reorder.io.regReadyAddr
  decode.io.branchSucc := bru.io.branchSucc
  decode.io.branchFail := bru.io.branchFail

  reorder.io.decodeValid := decode.io.decodeValid
  reorder.io.decodeInstrs := decode.io.decodeInstrs
  reorder.io.decodeMeta := decode.io.decodeMeta
  reorder.io.branchSucc := bru.io.branchSucc
  reorder.io.branchFail := bru.io.branchFail

  regrw.io.inValid := reorder.io.issueValid
  regrw.io.inInstrs := reorder.io.issueInstrs
  regrw.io.inBranch := reorder.io.issueBranch
  require(c.NumWritePhyRegs == 2)
  regrw.io.regWriteValid(0) := alu0.io.outValid
  regrw.io.regWriteAddr(0) := alu0.io.outRegAddr
  regrw.io.regWriteData(0) := alu0.io.outRegData
  regrw.io.regWriteValid(1) := alu1.io.outValid
  regrw.io.regWriteAddr(1) := alu1.io.outRegAddr
  regrw.io.regWriteData(1) := alu1.io.outRegData
  regrw.io.branchFail := bru.io.branchFail

  require(c.NumAluInstrs == 2)
  alu0.io.instr := regrw.io.aluInstrs(0)
  alu0.io.valid := regrw.io.aluValid(0)
  alu1.io.instr := regrw.io.aluInstrs(1)
  alu1.io.valid := regrw.io.aluValid(1)

  bru.io.instr := regrw.io.bruInstr
  bru.io.valid := regrw.io.bruValid

  io.halt := decode.io.halt
  io.unused := regrw.io.regWriteData
}
