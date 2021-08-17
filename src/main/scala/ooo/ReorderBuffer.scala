package pkucs.carbonchip.ooo

import chisel3._
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.Instruction

class ReorderBuffer(implicit c: ChipConfig) extends Module {
  implicit class SeqHelper(val seq: Seq[Bits]) {
    def asUInt(): UInt = VecInit(seq).asUInt()
  }

  val io = IO(new Bundle {
    val decodeReady = Output(Vec(c.NumDecodeInstrs, Bool()))
    val decodeValid = Input(Vec(c.NumDecodeInstrs, Bool()))
    val decodeInstrs = Input(Vec(c.NumDecodeInstrs, Instruction()))
    val decodeRegReady = Input(
      Vec(c.NumDecodeInstrs, Vec(c.NumReadRegsPerInstr, Bool()))
    )

    val issueValid = Output(Vec(c.NumIssueInstrs, Bool()))
    val issueInstrs = Output(Vec(c.NumIssueInstrs, Instruction()))

    val commitValid = Output(Vec(c.NumCommitInstrs, Bool()))
    val commitRegs = Output(Vec(c.NumCommitInstrs, UInt(c.BitNumPhyRegs.W)))

    val regReadyValid = Output(Vec(c.NumReadyRegs, Bool()))
    val regReadyAddr = Output(Vec(c.NumReadyRegs, UInt(c.BitNumPhyRegs.W)))
  })

  val buffer = Reg(Vec(c.NumReorderInstrs, Instruction()))
  val head = RegInit(0.U(c.BitNumReorderInstrs.W))
  val tail = RegInit(0.U(c.BitNumReorderInstrs.W))
  val valid = RegInit(VecInit(Seq.fill(c.NumReorderInstrs)(false.B)))
  val issued = RegInit(VecInit(Seq.fill(c.NumReorderInstrs)(false.B)))
  val ready = Reg(Vec(c.NumReorderInstrs, Vec(c.NumReadRegsPerInstr, Bool())))

  for (i <- 0 until c.NumReorderInstrs) {
    for (j <- 0 until c.NumReadRegsPerInstr) {
      for (k <- 0 until c.NumReadyRegs) {
        when(
          io.regReadyValid(k) &&
            io.regReadyAddr(k) === buffer(i).raddr(j).asUInt()
        ) {
          ready(i)(j) := true.B
        }
      }
    }
  }

  require(c.NumDecodeInstrs == 2)
  when(head + 1.U =/= tail) {
    buffer(head) := io.decodeInstrs(0)
    ready(head) := io.decodeRegReady(0)
    valid(head) := io.decodeValid(0)
    io.decodeReady(0) := true.B

    when(head + 2.U =/= tail) {
      buffer(head + 1.U) := io.decodeInstrs(1)
      ready(head + 1.U) := io.decodeRegReady(1)
      valid(head + 1.U) := io.decodeValid(1)
      io.decodeReady(1) := true.B

      when(io.decodeValid(1)) {
        assert(io.decodeValid(0))
        head := head + 2.U
      }.elsewhen(io.decodeValid(0)) {
        head := head + 1.U
      }
    }.otherwise {
      io.decodeReady(1) := false.B
      when(io.decodeValid(0)) {
        head := head + 1.U
      }
    }
  }.otherwise {
    io.decodeReady(0) := false.B
    io.decodeReady(1) := false.B
  }

  require(c.NumCommitInstrs == 2)
  io.commitRegs(0) := buffer(tail).caddr.asUInt()
  io.commitRegs(1) := buffer(tail + 1.U).caddr.asUInt()
  when(issued(tail)) {
    io.commitValid(0) := true.B
    issued(tail) := false.B

    when(issued(tail + 1.U)) {
      tail := tail + 2.U
      io.commitValid(1) := true.B
      issued(tail + 1.U) := false.B
    }.otherwise {
      tail := tail + 1.U
      io.commitValid(1) := false.B
    }
  }.otherwise {
    io.commitValid(0) := false.B
    io.commitValid(1) := false.B
  }

  val canIssue = Wire(Vec(c.NumReorderInstrs, Bool()))
  for (i <- 0 until c.NumReorderInstrs) {
    canIssue(i) := valid(i) && ready(i).asUInt().andR()
  }

  require(c.NumIssueInstrs == 2)
  require(c.NumReadyRegs == 2)
  require(c.NumWriteRegsPerInstr == 1)

  val issueIndex0 = Wire(Vec(c.BitNumReorderInstrs, Bool()))
  val issueIndex0Helpers =
    (0 to c.BitNumReorderInstrs).map(x => Wire(Vec(1 << x, Bool())))
  issueIndex0Helpers(c.BitNumReorderInstrs) := canIssue

  for (i <- 0 until c.BitNumReorderInstrs) {
    when(issueIndex0Helpers(i + 1).asUInt()((1 << i) - 1, 0).orR()) {
      issueIndex0(i) := false.B
      issueIndex0Helpers(i) := issueIndex0Helpers(i + 1)
        .asUInt()((1 << i) - 1, 0)
        .asBools()
    }.otherwise {
      issueIndex0(i) := true.B
      issueIndex0Helpers(i) := issueIndex0Helpers(i + 1)
        .asUInt()((1 << (i + 1)) - 1, 1 << i)
        .asBools()
    }
  }

  val issueInstr0 = Wire(Instruction())
  issueInstr0 := buffer(issueIndex0.asUInt())
  io.issueInstrs(0) := RegNext(issueInstr0)
  io.regReadyAddr(0) := issueInstr0.waddr.asUInt()

  val issueIndex1 = Wire(Vec(c.BitNumReorderInstrs, Bool()))
  val issueIndex1Helpers =
    (0 to c.BitNumReorderInstrs).map(x => Wire(Vec(1 << x, Bool())))
  issueIndex1Helpers(c.BitNumReorderInstrs) := canIssue

  for (i <- 0 until c.BitNumReorderInstrs) {
    when(issueIndex1Helpers(i + 1).asUInt()((1 << (i + 1)) - 1, 1 << i).orR()) {
      issueIndex1(i) := true.B
      issueIndex1Helpers(i) := issueIndex1Helpers(i + 1)
        .asUInt()((1 << (i + 1)) - 1, 1 << i)
        .asBools()
    }.otherwise {
      issueIndex1(i) := false.B
      issueIndex1Helpers(i) := issueIndex1Helpers(i + 1)
        .asUInt()((1 << i) - 1, 0)
        .asBools()
    }
  }

  val issueInstr1 = Wire(Instruction())
  issueInstr1 := buffer(issueIndex1.asUInt())
  io.issueInstrs(1) := RegNext(issueInstr1)
  io.regReadyAddr(1) := issueInstr1.waddr.asUInt()

  val issueValid = RegInit(false.B)
  io.issueValid(0) := issueValid
  io.issueValid(1) := issueValid

  when(canIssue.asUInt().orR()) {
    issueValid := true.B
    valid(issueIndex0.asUInt()) := false.B
    valid(issueIndex1.asUInt()) := false.B
    issued(issueIndex0.asUInt()) := true.B
    issued(issueIndex1.asUInt()) := true.B

    io.regReadyValid(0) := true.B
    io.regReadyValid(1) := true.B
  }.otherwise {
    issueValid := false.B
    io.regReadyValid(0) := false.B
    io.regReadyValid(1) := false.B
  }
}
