package pkucs.carbonchip.units

import chisel3._
import chisel3.util.Cat
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.Instruction
import pkucs.carbonchip.instr.InstructionMeta
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
    val decodeMeta = Output(Vec(c.NumDecodeInstrs, InstructionMeta()))

    val regCommitEna = Input(Vec(c.NumCommitRegs, Bool()))
    val phyRegCommit = Input(Vec(c.NumCommitRegs, UInt(c.BitNumPhyRegs.W)))

    val regReadyFlag = Input(Vec(c.NumReadyRegs, Bool()))
    val regReadyAddr = Input(Vec(c.NumReadyRegs, UInt(c.BitNumPhyRegs.W)))

    val setPcFlag = Input(Bool())
    val setPcData = Input(UInt(c.NumProgCounterBits.W))

    val branchSucc = Input(Bool())
    val branchFail = Input(Bool())

    val halt = Output(Bool())
  })

  val mapTable = Module(new MapTable)
  mapTable.io.regCommitEna := io.regCommitEna
  mapTable.io.phyRegCommit := io.phyRegCommit

  val readyTable = Module(new ReadyTable)
  readyTable.io.regReadyFlag := io.regReadyFlag
  readyTable.io.regReadyAddr := io.regReadyAddr

  val branchHead = RegInit(0.U(c.BitNumPendingBranches.W))
  val branchTail = RegInit(0.U(c.BitNumPendingBranches.W))
  val branchDest = Reg(Vec(c.NumPendingBranches, UInt(c.NumProgCounterBits.W)))
  val branchFlag = RegInit(VecInit(Seq.fill(c.NumPendingBranches)(false.B)))
  mapTable.io.backupIndex := branchHead
  mapTable.io.restoreIndex := branchTail

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
        val meta = InstructionMeta()
      }
    )
  )
  for (i <- 0 until c.NumDecodeInstrs) {
    io.decodeInstrs(i) := instrs(i).data
    io.decodeMeta(i) := instrs(i).meta
    for (j <- 0 until c.NumReadRegsPerInstr) {
      readyTable.io.phyRegRead(i * c.NumReadRegsPerInstr + j) :=
        VecInit(instrs(i).data.raddr(j)).asUInt()
      io.decodeMeta(i).ready(j) := instrs(i).meta.ready(j) ||
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

  require(c.NumDecodeInstrs == 2 && c.NumFetchInstrs == 2)

  val jalrWritePc = RegInit(false.B)
  val jalrWaitPc = RegInit(false.B)
  for (i <- 0 until c.NumDecodeInstrs) {
    decoders(i).io.jalrWritePc := jalrWritePc
  }

  val fetchValid = Wire(Bool())
  fetchValid := io.fetchValid && !jalrWaitPc

  val ready = Wire(Bool())
  ready := !valid(0) || (!valid(1) && io.decodeReady(0)) || io.decodeReady(1)
  io.halt := fetchValid &&
    Mux(fetchOffset === 0.U, !decoders(0).io.valid, !decoders(1).io.valid)

  val branchOk = Wire(Bool())
  branchOk := !branchFlag(branchHead)

  when(
    ready && fetchValid && (
      (decoders(1).io.isJumpReg && (fetchOffset === 1.U ||
        (decoders(0).io.valid && !decoders(0).io.isJump &&
          (!decoders(0).io.isBranch || branchOk)))) ||
        (decoders(0).io.isJumpReg && fetchOffset === 0.U)
    )
  ) {
    jalrWritePc := true.B
  }

  when(ready) {
    jalrWaitPc := jalrWritePc
  }

  def set(to: Integer, from: Integer) {
    validNext(to) := true.B
    instrs(to).data := decoders(from).io.cooked
    instrs(to).meta := decoders(from).io.meta
  }

  def clear(to: Integer) {
    validNext(to) := false.B
    instrs(to) := DontCare
  }

  def jump(target: UInt) {
    fetchBaseNext := target(c.NumProgCounterBits - 1, c.BitNumFetchInstrs)
    fetchOffsetNext := target(c.BitNumFetchInstrs - 1, 0)
  }

  when(io.branchFail || io.setPcFlag) {
    clear(0)
    clear(1)
    when(io.setPcFlag) {
      jump(io.setPcData)
    }.otherwise {
      jump(branchDest(branchTail))
    }
  }.elsewhen(ready) {
    when(!fetchValid) {
      clear(0)
      clear(1)
      fetchBaseNext := fetchBase
      fetchOffsetNext := fetchOffset
    }.elsewhen(fetchOffset === 0.U) {
      when(!decoders(0).io.valid || (decoders(0).io.isBranch && !branchOk)) {
        clear(0)
        clear(1)
        fetchBaseNext := fetchBase
        fetchOffsetNext := fetchOffset
      }.elsewhen(
        decoders(0).io.isJump || !decoders(1).io.valid ||
          (decoders(0).io.isBranch && decoders(1).io.isBranch) ||
          (decoders(1).io.isBranch && !branchOk)
      ) {
        set(0, 0)
        clear(1)
        when(decoders(0).io.isJump) {
          jump(decoders(0).io.jumpPc)
        }.otherwise {
          fetchBaseNext := fetchBase
          fetchOffsetNext := 1.U
        }
      }.otherwise {
        set(0, 0)
        set(1, 1)
        when(decoders(1).io.isJump) {
          jump(decoders(1).io.jumpPc)
        }.otherwise {
          fetchBaseNext := fetchBase + 1.U
          fetchOffsetNext := 0.U
        }
      }
    }.otherwise {
      when(!decoders(1).io.valid || (decoders(1).io.isBranch && !branchOk)) {
        clear(0)
        clear(1)
        fetchBaseNext := fetchBase
        fetchOffsetNext := fetchOffset
      }.otherwise {
        set(0, 1)
        clear(1)
        when(decoders(1).io.isJump) {
          jump(decoders(1).io.jumpPc)
        }.otherwise {
          fetchBaseNext := fetchBase + 1.U
          fetchOffsetNext := 0.U
        }
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

  when(io.branchSucc) {
    branchFlag(branchTail) := false.B
    branchTail := branchTail + 1.U
  }

  val branch0 = Wire(Bool())
  branch0 := decoders(0).io.isBranch && fetchOffset === 0.U
  mapTable.io.backupValid := branchOk
  mapTable.io.backupInstr := Mux(branch0, 0.U, 1.U)
  when(branchOk) {
    branchDest(branchHead) := Mux(
      branch0,
      decoders(0).io.branchPc,
      decoders(1).io.branchPc
    )
  }
  when(
    ready && branchOk && fetchValid && (
      (decoders(1).io.isBranch && (fetchOffset === 1.U ||
        (decoders(0).io.valid && !decoders(0).io.isJump))) ||
        (decoders(0).io.isBranch && fetchOffset === 0.U)
    )
  ) {
    branchHead := branchHead + 1.U
    branchFlag(branchHead) := true.B
  }

  when(io.branchFail || io.setPcFlag) {
    jalrWritePc := false.B
    jalrWaitPc := false.B
  }

  when(io.branchFail) {
    branchHead := 0.U
    branchTail := 0.U
    for (i <- 0 until c.NumPendingBranches) {
      branchFlag(i) := false.B
    }
    mapTable.io.restoreValid := true.B
  }.otherwise {
    mapTable.io.restoreValid := false.B
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
        decoders(i).io.regWriteEna(j) &&
          (if (i == 0) fetchOffset === 0.U
           else
             (fetchOffset === 1.U ||
             (decoders(0).io.valid && !decoders(1).io.isJump &&
               (!decoders(0).io.isBranch || branchOk))))
      mapTable.io.isaRegWrite(i * c.NumWriteRegsPerInstr + j) :=
        decoders(i).io.isaRegWrite(j)
      decoders(i).io.phyRegWrNew(j) :=
        mapTable.io.phyRegWrNew(i * c.NumWriteRegsPerInstr + j)
      decoders(i).io.phyRegWrOld(j) :=
        mapTable.io.phyRegWrOld(i * c.NumWriteRegsPerInstr + j)
    }
  }
  mapTable.io.regWriteUpd := ready && fetchValid

  readyTable.io.phyRegFree := mapTable.io.phyRegFree
}
