package pkucs.carbonchip.config

class ChipConfig {
  val NumIsaRegs = 32
  val BitNumIsaRegs = 5

  val NumPhyRegs = 64
  val BitNumPhyRegs = 6

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

  val NumInstrBits = 32
  val NumProgCounterBits = 30
  val NumFetchInstrBits = 64
  val NumMemInstrsByFetch = 128
  val NumFetchAddrBits = 29

  val NumReorderInstrs = 16
  val BitNumReorderInstrs = 4
}
