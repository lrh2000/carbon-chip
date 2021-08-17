package pkucs.carbonchip.units

import chisel3._
import chisel3.util.Cat
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.Instruction
import pkucs.carbonchip.instr.InstrDecoder
import pkucs.carbonchip.ooo.MapTable
import pkucs.carbonchip.ooo.ReadyTable

class DecodeUnit(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val fetchAddrIn = Input(UInt(c.NumFetchAddrBits.W))
    val fetchAddrOut = Output(UInt(c.NumFetchAddrBits.W))
    val fetchValid = Input(Bool())
    val fetchInstrs = Input(Vec(c.NumFetchInstrs, UInt(c.NumInstrBits.W)))

    val decodeReady = Input(Vec(c.NumDecodeInstrs, Bool()))
    val decodeValid = Output(Vec(c.NumDecodeInstrs, Bool()))
    val decodeInstrs = Output(Vec(c.NumDecodeInstrs, Instruction()))
    val decodeRegReady = Output(
      Vec(c.NumDecodeInstrs, Vec(c.NumReadRegsPerInstr, Bool()))
    )

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

  val instrs = Reg(
    Vec(
      c.NumDecodeInstrs,
      new Bundle {
        val data = Instruction()
        val flag = Vec(c.NumReadRegsPerInstr, Bool())
      }
    )
  )
  for (i <- 0 until c.NumDecodeInstrs) {
    io.decodeInstrs(i) := instrs(i).data
    for (j <- 0 until c.NumReadRegsPerInstr) {
      readyTable.io.phyRegRead(i * c.NumReadRegsPerInstr + j) :=
        VecInit(instrs(i).data.raddr(j)).asUInt()
      io.decodeRegReady(i)(j) := instrs(i).flag(j) ||
        readyTable.io.phyRegFlag(i * c.NumReadRegsPerInstr + j)
    }
  }

  require(c.NumFetchInstrs == c.NumDecodeInstrs)
  val decoders = Array.fill(c.NumDecodeInstrs)(Module(new InstrDecoder))
  for (i <- 0 until c.NumDecodeInstrs) {
    decoders(i).io.pc := Cat(
      fetchBase,
      i.U((c.NumProgCounterBits - c.NumFetchAddrBits).W)
    )
    decoders(i).io.raw := io.fetchInstrs(i)
  }

  val ready = Wire(Bool())
  val halt0 = Wire(Bool())
  val halt1 = Wire(Bool())

  require(c.NumDecodeInstrs == 2 && c.NumFetchInstrs == 2)
  ready := !valid(0) || (!valid(1) && io.decodeReady(0)) || io.decodeReady(1)
  halt0 := Mux(
    fetchOffset === 0.U,
    !decoders(0).io.valid,
    !decoders(1).io.valid
  )
  halt1 := !decoders(1).io.valid
  io.halt := io.fetchValid && halt0

  def set(to: Integer, from: Integer) {
    validNext(to) := true.B
    instrs(to).data := decoders(from).io.cooked
    for (i <- 0 until c.NumReadRegsPerInstr) {
      instrs(to).flag(i) := !decoders(from).io.regReadEna(i)
    }
  }

  def clear(to: Integer) {
    validNext(to) := false.B
    instrs(to) := DontCare
  }

  when(ready) {
    when(!io.fetchValid || halt0) {
      clear(0)
      clear(1)
      fetchBaseNext := fetchBase
      fetchOffsetNext := fetchOffset
    }.otherwise {
      when(halt1) {
        set(0, 0)
        clear(1)
        fetchBaseNext := fetchBase
        fetchOffsetNext := 1.U
      }.elsewhen(fetchOffset === 1.U) {
        set(0, 1)
        clear(1)
        fetchBaseNext := fetchBase + 1.U
        fetchOffsetNext := 0.U
      }.otherwise {
        set(0, 0)
        set(1, 1)
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

  require(c.NumDecodeInstrs == 2)
  for (i <- 0 until c.NumDecodeInstrs) {
    for (j <- 0 until c.NumReadRegsPerInstr) {
      mapTable.io.isaRegRead(i * c.NumReadRegsPerInstr + j) :=
        decoders(i).io.isaRegRead(j)
      decoders(i).io.phyRegRead(j) :=
        mapTable.io.phyRegRead(i * c.NumReadRegsPerInstr + j)
    }
    for (j <- 0 until c.NumWriteRegsPerInstr) {
      mapTable.io.regWriteEna(i * c.NumWriteRegsPerInstr + j) :=
        decoders(i).io.regWriteEna(j) && decoders(0).io.valid &&
          (if (i == 0) fetchOffset === 0.U else decoders(1).io.valid)
      mapTable.io.isaRegWrite(i * c.NumWriteRegsPerInstr + j) :=
        decoders(i).io.isaRegWrite(j)
      decoders(i).io.phyRegWrNew(j) :=
        mapTable.io.phyRegWrNew(i * c.NumWriteRegsPerInstr + j)
      decoders(i).io.phyRegWrOld(j) :=
        mapTable.io.phyRegWrOld(i * c.NumWriteRegsPerInstr + j)
    }
  }
  mapTable.io.regWriteUpd := ready && io.fetchValid

  readyTable.io.phyRegWrite := mapTable.io.phyRegWrNew
}
