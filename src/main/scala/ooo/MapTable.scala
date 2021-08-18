package pkucs.carbonchip.ooo

import chisel3._
import pkucs.carbonchip.config.ChipConfig

class MapTable(implicit c: ChipConfig) extends Module {
  val io = IO(new Bundle {
    val isaRegRead = Input(Vec(c.NumReadIsaRegs, UInt(c.BitNumIsaRegs.W)))
    val phyRegRead = Output(Vec(c.NumReadIsaRegs, UInt(c.BitNumPhyRegs.W)))

    // Since register x0 is hardwired with all bits equal to 0, do we
    // still need to have a separate write enable signal?
    val regWriteEna = Input(Vec(c.NumWriteIsaRegs, Bool()))
    val isaRegWrite = Input(Vec(c.NumWriteIsaRegs, UInt(c.BitNumIsaRegs.W)))
    val phyRegWrNew = Output(Vec(c.NumWriteIsaRegs, UInt(c.BitNumPhyRegs.W)))
    val phyRegWrOld = Output(Vec(c.NumWriteIsaRegs, UInt(c.BitNumPhyRegs.W)))
    val regWriteUpd = Input(Bool())

    val phyRegFree = Output(Vec(c.NumWriteIsaRegs, UInt(c.BitNumPhyRegs.W)))

    val regCommitEna = Input(Vec(c.NumCommitRegs, Bool()))
    val phyRegCommit = Input(Vec(c.NumCommitRegs, UInt(c.BitNumPhyRegs.W)))
  })

  val mapping = RegInit(
    VecInit((0 until c.NumIsaRegs).map(_.U(c.BitNumPhyRegs.W)))
  )
  val freeRegs = RegInit(
    VecInit(
      Seq.fill(c.NumIsaRegs)(false.B)
        ++ Seq.fill(c.NumPhyRegs - c.NumIsaRegs)(true.B)
    )
  )

  val mappingOut = Wire(Vec(c.NumIsaRegs, UInt(c.BitNumPhyRegs.W)))
  for (i <- 0 until c.NumIsaRegs) {
    mappingOut(i) := (if (i == c.IsaRegZeroAddr) c.PhyRegZeroAddr.U
                      else mapping(i))
  }

  val freeRegsOut = Wire(Vec(c.NumPhyRegs, Bool()))
  for (i <- 0 until c.NumPhyRegs) {
    freeRegsOut(i) := (if (i == c.PhyRegZeroAddr) false.B
                       else freeRegs(i))
  }

  for (i <- 0 until c.NumReadIsaRegs) {
    io.phyRegRead(i) := mappingOut(io.isaRegRead(i))

    for (j <- 0 until i / c.NumReadRegsPerInstr * c.NumWriteRegsPerInstr) {
      when(io.regWriteEna(j) && io.isaRegWrite(j) === io.isaRegRead(i)) {
        io.phyRegRead(i) := io.phyRegWrNew(j)
      }
    }
  }

  for (i <- 0 until c.NumWriteIsaRegs) {
    io.phyRegWrOld(i) := mappingOut(io.isaRegWrite(i))

    for (j <- 0 until i / c.NumWriteRegsPerInstr * c.NumWriteRegsPerInstr) {
      when(io.regWriteEna(j) && io.isaRegWrite(j) === io.isaRegWrite(i)) {
        io.phyRegWrOld(i) := io.phyRegWrNew(j)
      }
    }
  }

  val freeReg0 = Wire(Vec(c.BitNumPhyRegs, Bool()))
  val freeReg0Helpers =
    (0 to c.BitNumPhyRegs).map(x => Wire(Vec(1 << x, Bool())))
  freeReg0Helpers(c.BitNumPhyRegs) := freeRegsOut

  for (i <- 0 until c.BitNumPhyRegs) {
    when(freeReg0Helpers(i + 1).asUInt()((1 << i) - 1, 0).orR()) {
      freeReg0(i) := false.B
      freeReg0Helpers(i) := freeReg0Helpers(i + 1)
        .asUInt()((1 << i) - 1, 0)
        .asBools()
    }.otherwise {
      freeReg0(i) := true.B
      freeReg0Helpers(i) := freeReg0Helpers(i + 1)
        .asUInt()((1 << (i + 1)) - 1, 1 << i)
        .asBools()
    }
  }

  val freeReg1 = Wire(Vec(c.BitNumPhyRegs, Bool()))
  val freeReg1Helpers =
    (0 to c.BitNumPhyRegs).map(x => Wire(Vec(1 << x, Bool())))
  freeReg1Helpers(c.BitNumPhyRegs) := freeRegsOut

  for (i <- 0 until c.BitNumPhyRegs) {
    when(freeReg1Helpers(i + 1).asUInt()((1 << (i + 1)) - 1, 1 << i).orR()) {
      freeReg1(i) := true.B
      freeReg1Helpers(i) := freeReg1Helpers(i + 1)
        .asUInt()((1 << (i + 1)) - 1, 1 << i)
        .asBools()
    }.otherwise {
      freeReg1(i) := false.B
      freeReg1Helpers(i) := freeReg1Helpers(i + 1)
        .asUInt()((1 << i) - 1, 0)
        .asBools()
    }
  }

  require(c.NumWriteIsaRegs == 2)

  io.phyRegFree(0) := freeReg0.asUInt()
  io.phyRegFree(1) := freeReg1.asUInt()

  when(io.regWriteUpd && io.regWriteEna(0)) {
    mapping(io.isaRegWrite(0)) := freeReg0.asUInt()
  }
  when(io.regWriteUpd && io.regWriteEna(1)) {
    mapping(io.isaRegWrite(1)) := freeReg1.asUInt()
  }

  for (i <- 0 until c.NumWriteIsaRegs) {
    when(io.isaRegWrite(i) === c.IsaRegZeroAddr.U) {
      io.phyRegWrNew(i) := c.PhyRegZeroAddr.U
    }.otherwise {
      io.phyRegWrNew(i) := io.phyRegFree(i)
    }
  }

  for (i <- 0 until c.NumPhyRegs) {
    when(
      io.regWriteUpd && io.regWriteEna(0)
        && io.isaRegWrite(0) =/= c.IsaRegZeroAddr.U
        && (if (i - 1 < 0) true.B
            else ~freeRegsOut.asUInt()(i - 1, 0).orR())
    ) {
      freeRegs(i) := false.B
    }
    when(
      io.regWriteUpd && io.regWriteEna(1)
        && io.isaRegWrite(1) =/= c.IsaRegZeroAddr.U
        && (if (c.NumPhyRegs - 1 < i + 1) true.B
            else ~freeRegsOut.asUInt()(c.NumPhyRegs - 1, i + 1).orR())
    ) {
      freeRegs(i) := false.B
    }

    for (j <- 0 until c.NumCommitRegs) {
      when(io.regCommitEna(j) && io.phyRegCommit(j) === i.U) {
        freeRegs(i) := true.B
      }
    }
  }
}
