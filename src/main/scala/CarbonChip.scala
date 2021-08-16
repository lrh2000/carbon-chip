package pkucs.carbonchip

import chisel3._
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.units.FetchUnit
import pkucs.carbonchip.units.DecodeUnit
import pkucs.carbonchip.ooo.ReorderBuffer
import pkucs.carbonchip.units.RegisterUnit
import pkucs.carbonchip.units.ArithmeticLogicUnit

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

  fetch.io.fetchAddrIn := decode.io.fetchAddrOut

  decode.io.fetchAddrIn := fetch.io.fetchAddrOut
  decode.io.fetchValid := fetch.io.fetchValid
  decode.io.fetchInstrs := fetch.io.fetchInstrs
  decode.io.decodeReady := reorder.io.decodeReady
  decode.io.regCommitEna := reorder.io.commitValid
  decode.io.phyRegCommit := reorder.io.commitRegs
  decode.io.regReadyFlag := reorder.io.regReadyValid
  decode.io.regReadyAddr := reorder.io.regReadyAddr

  reorder.io.decodeValid := decode.io.decodeValid
  reorder.io.decodeInstrs := decode.io.decodeInstrs

  regrw.io.inValid := reorder.io.issueValid
  regrw.io.inInstrs := reorder.io.issueInstrs
  require(c.NumWritePhyRegs == 2)
  regrw.io.regWriteValid(0) := alu0.io.outValid
  regrw.io.regWriteAddr(0) := alu0.io.outRegAddr
  regrw.io.regWriteData(0) := alu0.io.result
  regrw.io.regWriteValid(1) := alu1.io.outValid
  regrw.io.regWriteAddr(1) := alu1.io.outRegAddr
  regrw.io.regWriteData(1) := alu1.io.result

  require(c.NumIssueInstrs == 2)
  require(c.NumWriteRegsPerInstr == 1)
  alu0.io.operands := regrw.io.outInstrs(0).inRegData
  alu0.io.inRegAddr := regrw.io.outInstrs(0).outRegAddr(0)
  alu0.io.inValid := regrw.io.outValid(0)
  alu1.io.operands := regrw.io.outInstrs(1).inRegData
  alu1.io.inRegAddr := regrw.io.outInstrs(1).outRegAddr(0)
  alu1.io.inValid := regrw.io.outValid(1)

  io.halt := decode.io.halt
  io.unused := regrw.io.regWriteData
}
