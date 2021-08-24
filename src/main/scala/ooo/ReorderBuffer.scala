package pkucs.carbonchip.ooo

import chisel3._
import pkucs.carbonchip.config.ChipConfig
import pkucs.carbonchip.instr.Instruction
import pkucs.carbonchip.instr.InstructionMeta

class ReorderBuffer(implicit c: ChipConfig) extends Module {
  implicit class SeqHelper(val seq: Seq[Bits]) {
    def asUInt(): UInt = VecInit(seq).asUInt()
  }

  val io = IO(new Bundle {
    val decodeReady = Output(Vec(c.NumDecodeInstrs, Bool()))
    val decodeValid = Input(Vec(c.NumDecodeInstrs, Bool()))
    val decodeInstrs = Input(Vec(c.NumDecodeInstrs, Instruction()))
    val decodeMeta = Input(Vec(c.NumDecodeInstrs, InstructionMeta()))

    val issueValid = Output(Vec(c.NumIssueInstrs, Bool()))
    val issueInstrs = Output(Vec(c.NumIssueInstrs, Instruction()))
    val issueBranch = Output(Bool())

    val commitValid = Output(Vec(c.NumCommitInstrs, Bool()))
    val commitRegs = Output(Vec(c.NumCommitInstrs, UInt(c.BitNumPhyRegs.W)))

    val regReadyValid = Output(Vec(c.NumReadyRegs, Bool()))
    val regReadyAddr = Output(Vec(c.NumReadyRegs, UInt(c.BitNumPhyRegs.W)))

    val branchSucc = Input(Bool())
    val branchFail = Input(Bool())
  })

  // valid=0, issued=0  invalid
  // valid=1, issued=0  to be issued
  // valid=0, issued=1  issued
  // valid=1, issued=1  to be committed
  val buffer = Reg(Vec(c.NumReorderInstrs, Instruction()))
  val head = RegInit(0.U(c.BitNumReorderInstrs.W))
  val tail = RegInit(0.U(c.BitNumReorderInstrs.W))
  val valid = RegInit(VecInit(Seq.fill(c.NumReorderInstrs)(false.B)))
  val issued = RegInit(VecInit(Seq.fill(c.NumReorderInstrs)(false.B)))
  val meta = Reg(Vec(c.NumReorderInstrs, InstructionMeta()))

  for (i <- 0 until c.NumReorderInstrs) {
    for (j <- 0 until c.NumReadRegsPerInstr) {
      for (k <- 0 until c.NumReadyRegs) {
        when(
          io.regReadyValid(k) &&
            io.regReadyAddr(k) === buffer(i).raddr(j).asUInt()
        ) {
          meta(i).ready(j) := true.B
        }
      }
    }
  }

  val branchIndices = Reg(
    Vec(c.NumPendingBranches, UInt(c.BitNumReorderInstrs.W))
  )
  val branchTail = RegInit(0.U(c.BitNumPendingBranches.W))
  val branchHead = RegInit(0.U(c.BitNumPendingBranches.W))
  val branchIsEmpty = RegInit(true.B)
  val branchWritePos = Wire(UInt(c.BitNumPendingBranches.W))
  val branchWillIssue = Wire(Bool())
  val branchWillAdd = Wire(Bool())
  val branchWillDel = Wire(Bool())

  when(branchWillAdd && !branchWillIssue) {
    branchIsEmpty := false.B
  }.elsewhen(
    !branchWillAdd && branchWillIssue && branchHead === 1.U
  ) {
    branchIsEmpty := true.B
  }

  when(branchWillIssue) {
    branchWritePos := branchHead - 1.U
  }.otherwise {
    branchWritePos := branchHead
  }

  when(branchWillIssue) {
    for (i <- 0 until c.NumPendingBranches) {
      branchIndices((if (i == 0) c.NumPendingBranches else i) - 1) :=
        branchIndices(i)
    }
  }

  when(branchWillIssue && !branchWillAdd) {
    branchHead := branchHead - 1.U
  }.elsewhen(!branchWillIssue && branchWillAdd) {
    branchHead := branchHead + 1.U
  }

  when(branchWillIssue && !branchWillDel) {
    branchTail := branchTail - 1.U
  }.elsewhen(!branchWillIssue && branchWillDel) {
    branchTail := branchTail + 1.U
  }

  require(c.NumDecodeInstrs == 2)
  when(head + 1.U =/= tail) {
    buffer(head) := io.decodeInstrs(0)
    meta(head) := io.decodeMeta(0)
    valid(head) := io.decodeValid(0)
    io.decodeReady(0) := true.B

    when(head + 2.U =/= tail) {
      buffer(head + 1.U) := io.decodeInstrs(1)
      meta(head + 1.U) := io.decodeMeta(1)
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

  when(
    (io.decodeValid(0) && io.decodeReady(0) && !io.decodeMeta(0).alu) ||
      (io.decodeValid(1) && io.decodeReady(1) && !io.decodeMeta(1).alu)
  ) {
    branchWillAdd := true.B
    branchIndices(branchWritePos) :=
      Mux(io.decodeMeta(0).alu, head + 1.U, head)
  }.otherwise {
    branchWillAdd := false.B
  }

  val branchCompleteIndex = Wire(UInt(c.BitNumReorderInstrs.W))
  branchCompleteIndex := branchIndices(branchTail)

  branchWillDel := io.branchSucc
  when(io.branchSucc || io.branchFail) {
    valid(branchCompleteIndex) := true.B
  }

  val issueValid = RegInit(VecInit(Seq.fill(c.NumIssueInstrs)(false.B)))
  for (i <- 0 until c.NumIssueInstrs) {
    io.issueValid(i) := issueValid(i)
  }

  val canIssue = Wire(Vec(c.NumReorderInstrs, Bool()))
  for (i <- 0 until c.NumReorderInstrs) {
    canIssue(i) := valid(i) && !issued(i) &&
      meta(i).alu && meta(i).ready.asUInt().andR()
  }

  val canIssueR = Wire(Bool())
  canIssueR := canIssue.asUInt().orR()

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

  when(canIssueR) {
    issueValid(0) := true.B
    io.regReadyValid(0) := true.B

    issued(issueIndex0.asUInt()) := true.B
  }.otherwise {
    issueValid(0) := false.B
    io.regReadyValid(0) := false.B
  }

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

  val branchIssueIndex = Wire(UInt(c.BitNumReorderInstrs.W))
  branchIssueIndex := branchIndices(0)
  branchWillIssue := !branchIsEmpty && !io.branchFail &&
    meta(branchIssueIndex).ready.asUInt().andR()

  val branchInstr = Wire(Instruction())
  branchInstr := buffer(branchIssueIndex)
  val issueInstr1 = Wire(Instruction())
  issueInstr1 := buffer(issueIndex1.asUInt())

  io.issueInstrs(1) := RegNext(
    Mux(branchWillIssue, branchInstr, issueInstr1)
  )
  io.regReadyAddr(1) := issueInstr1.waddr.asUInt()
  io.issueBranch := RegNext(branchWillIssue)

  when(branchWillIssue) {
    assert(valid(branchIssueIndex) && !issued(branchIssueIndex))
    assert(!meta(branchIssueIndex).alu)
    issued(branchIssueIndex) := true.B
    valid(branchIssueIndex) := false.B

    issueValid(1) := true.B
    io.regReadyValid(1) := false.B
  }.elsewhen(canIssue.asUInt().orR()) {
    issued(issueIndex1.asUInt()) := true.B

    issueValid(1) := true.B
    io.regReadyValid(1) := true.B
  }.otherwise {
    issueValid(1) := false.B
    io.regReadyValid(1) := false.B
  }

  val mask = Wire(Vec(c.NumReorderInstrs, Bool()))
  when(head > branchCompleteIndex) {
    for (i <- 0 until c.NumReorderInstrs) {
      mask(i) := i.U > branchCompleteIndex && i.U < head
    }
  }.otherwise {
    for (i <- 0 until c.NumReorderInstrs) {
      mask(i) := i.U > branchCompleteIndex || i.U < head
    }
  }

  when(io.branchFail) {
    head := branchCompleteIndex + 1.U
    for (i <- 0 until c.NumReorderInstrs) {
      when(mask(i)) {
        issued(i) := false.B
        valid(i) := false.B
      }
    }

    branchHead := 0.U
    branchTail := 0.U
    branchIsEmpty := true.B
  }

  require(c.NumCommitInstrs == 2)
  io.commitRegs(0) := buffer(tail).caddr.asUInt()
  io.commitRegs(1) := buffer(tail + 1.U).caddr.asUInt()
  when(issued(tail) && valid(tail)) {
    io.commitValid(0) := meta(tail).alu
    issued(tail) := false.B
    valid(tail) := false.B

    when(issued(tail + 1.U) && valid(tail + 1.U)) {
      tail := tail + 2.U
      io.commitValid(1) := meta(tail + 1.U).alu
      issued(tail + 1.U) := false.B
      valid(tail + 1.U) := false.B
    }.otherwise {
      tail := tail + 1.U
      io.commitValid(1) := false.B
    }
  }.otherwise {
    io.commitValid(0) := false.B
    io.commitValid(1) := false.B
  }
}
