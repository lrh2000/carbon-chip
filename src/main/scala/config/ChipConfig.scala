package pkucs.carbonchip.config

class ChipConfig {
  val NumIsaRegs = 32
  val BitNumIsaRegs = 5

  val NumPhyRegs = 64
  val BitNumPhyRegs = 6

  val IsaRegZeroAddr = 0
  val PhyRegZeroAddr = 0
  val RegZeroData = 0

  val NumReadIsaRegs = 4
  val NumReadRegsPerInstr = 2

  val NumWriteIsaRegs = 2
  val NumWriteRegsPerInstr = 1

  val NumReadPhyRegs = 4
  val NumWritePhyRegs = 2

  val NumCommitRegs = 2
  val NumReadyRegs = 2

  val BitNumRegData = 32

  val NumFetchInstrs = 2
  val NumDecodeInstrs = 2
  val NumIssueInstrs = 2
  val NumCommitInstrs = 2
  val BitNumFetchInstrs = 1
  val BitNumDecodeInstrs = 1

  val NumInstrBits = 32
  val NumProgCounterBits = 30
  val NumFetchInstrBits = 64
  val NumMemInstrsByFetch = 128
  val NumFetchAddrBits = 29

  val NumReorderInstrs = 16
  val BitNumReorderInstrs = 4

  val NumAluInstrs = 2
  val NumAluShamtBits = 5

  val OpcodeLui = "b0110111"
  val OpcodeAuipc = "b0010111"
  val OpcodeAluImm = "b0010011"
  val OpcodeAluReg = "b0110011"
  val OpcodeBranch = "b1100011"

  val Funct3AddSub = "b000"
  val Funct3SrlSra = "b101"
  val Funct3Sll = "b001"

  val Funct3Beq = "b000"
  val Funct3Bne = "b001"
  val Funct3Blt = "b100"
  val Funct3Bge = "b101"
  val Funct3Bltu = "b110"
  val Funct3Bgeu = "b111"
  val Funct3BxxNegator = "b001"

  val BitNumAluFunct = 4
  val AluFunctAdd = "b0000"
  val AluFunctSub = "b1000"
  val AluFunctSll = "b0001"
  val AluFunctSlt = "b0010"
  val AluFunctSltu = "b0011"
  val AluFunctXor = "b0100"
  val AluFunctSrl = "b0101"
  val AluFunctSra = "b1101"
  val AluFunctOr = "b0110"
  val AluFunctAnd = "b0111"

  val NumPendingBranches = 4
  val BitNumPendingBranches = 2

  // Initial PC = 0x80000000
  val InitialFetchAddr = "h10000000"
}
