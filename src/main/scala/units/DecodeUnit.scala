package pkucs.carbonchip.units

import chisel3._
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.DecodedInstr
import pkucs.carbonchip.ooo.MapTable
import pkucs.carbonchip.ooo.ReadyTable

class DecodeUnit(implicit c : ChipConfig) extends Module {
  val io = IO(new Bundle {
    val fetchAddrIn = Input(UInt(c.NumFetchAddrBits.W))
    val fetchAddrOut = Output(UInt(c.NumFetchAddrBits.W))
    val fetchValid = Input(Bool())
    val fetchInstrs = Input(Vec(c.NumFetchInstrs, UInt(c.NumInstrBits.W)))

    val decodeReady = Input(Vec(c.NumDecodeInstrs, Bool()))
    val decodeValid = Output(Vec(c.NumDecodeInstrs, Bool()))
    val decodeInstrs = Output(Vec(c.NumDecodeInstrs, DecodedInstr()))

    val regCommitEna = Input(Vec(c.NumCommitRegs, Bool()))
    val phyRegCommit = Input(Vec(c.NumCommitRegs, UInt(c.BitNumPhyRegs.W)))

    val regReadyFlag = Input(Vec(c.NumReadyRegs, Bool()))
    val regReadyAddr = Input(Vec(c.NumReadyRegs, UInt(c.BitNumPhyRegs.W)))

    val halt = Output(Bool())
  })

  val mapTable = Module(new MapTable)
  mapTable.io.regCommitEna := io.regCommitEna
  mapTable.io.phyRegCommit := io.phyRegCommit

  val readyTable = Module(new ReadyTable)
  readyTable.io.regReadyFlag := io.regReadyFlag
  readyTable.io.regReadyAddr := io.regReadyAddr

  val fetchBaseNext = Wire(UInt(c.NumFetchAddrBits.W))
  val fetchOffsetNext = Wire(UInt(c.BitNumFetchInstrs.W))
  io.fetchAddrOut := fetchBaseNext

  val fetchBase = Wire(UInt(c.NumFetchAddrBits.W))
  val fetchOffset = RegNext(fetchOffsetNext, 0.U(c.BitNumFetchInstrs.W))
  fetchBase := io.fetchAddrIn

  val validNext = Wire(Vec(c.NumFetchInstrs, Bool()))
  val valid = RegNext(validNext, VecInit(Seq.fill(c.NumFetchInstrs)(false.B)))
  io.decodeValid := valid

  require(c.NumFetchInstrs == c.NumDecodeInstrs)
  val instrs = Reg(Vec(c.NumDecodeInstrs, DecodedInstr()))
  io.decodeInstrs := instrs
  for (i <- 0 until c.NumFetchInstrs) {
    for (j <- 0 until c.NumReadRegsPerInstr) {
      readyTable.io.phyRegRead(i * c.NumReadRegsPerInstr + j) := instrs(i).inRegAddr(j)
      io.decodeInstrs(i).readyFlags(j) := readyTable.io.phyRegFlag(i * c.NumReadRegsPerInstr + j)
    }
  }

  val instrsNext = Wire(Vec(c.NumDecodeInstrs, DecodedInstr()))
  val ready = Wire(Bool())
  val halt0 = Wire(Bool())
  val halt1 = Wire(Bool())

  require(c.NumDecodeInstrs == 2)
  ready := !valid(0) || (!valid(1) && io.decodeReady(0)) || io.decodeReady(1)
  halt0 := io.fetchInstrs(fetchOffset) === 0.U
  halt1 := io.fetchInstrs(1) === 0.U
  io.halt := io.fetchValid && halt0

  when(ready) {
    when(!io.fetchValid || halt0) {
      validNext(0) := false.B
      validNext(1) := false.B
      fetchBaseNext := fetchBase
      fetchOffsetNext := fetchOffset
    }.otherwise {
      validNext(0) := true.B
      instrs(1) := instrsNext(1)
      when(halt1) {
        validNext(1) := false.B
        instrs(0) := instrsNext(0)
        fetchBaseNext := fetchBase
        fetchOffsetNext := 1.U
      }.elsewhen(fetchOffset === 1.U) {
        validNext(1) := false.B
        instrs(0) := instrsNext(1)
        fetchBaseNext := fetchBase + 1.U
        fetchOffsetNext := 0.U
      }.otherwise {
        validNext(1) := true.B
        instrs(0) := instrsNext(0)
        fetchBaseNext := fetchBase + 1.U
        fetchOffsetNext := 0.U
      }
    }
  }.otherwise {
    validNext(0) := valid(0)
    when(io.decodeReady(0)) {
      validNext(1) := false.B
      instrs(0) := instrs(1)
    }.otherwise {
      validNext(1) := valid(1)
    }
    fetchBaseNext := fetchBase
    fetchOffsetNext := fetchOffset
  }

  require(c.NumReadRegsPerInstr == 2)
  require(c.NumWriteRegsPerInstr == 1)
  for (i <- 0 until c.NumFetchInstrs) {
    mapTable.io.isaRegRead(i * 2) := io.fetchInstrs(i)(19, 15)
    mapTable.io.isaRegRead(i * 2 + 1) := io.fetchInstrs(i)(24, 20)
    instrsNext(i).inRegAddr(0) := mapTable.io.phyRegRead(i * 2)
    instrsNext(i).inRegAddr(1) := mapTable.io.phyRegRead(i * 2 + 1)
    instrsNext(i).readyFlags(0) := false.B
    instrsNext(i).readyFlags(1) := false.B

    mapTable.io.regWriteEna(i) := validNext(i) && ready
    mapTable.io.isaRegWrite(i) := io.fetchInstrs(i)(11, 7)
    instrsNext(i).outRegAddr(0) := mapTable.io.phyRegWrNew(i)
    instrsNext(i).killedRegAddr(0) := mapTable.io.phyRegWrOld(i)

    readyTable.io.regWriteEna(i) := mapTable.io.regWriteEna(i)
    readyTable.io.phyRegWrite(i) := mapTable.io.phyRegWrNew(i)
  }
}
