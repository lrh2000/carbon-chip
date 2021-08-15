package pkucs.carbonchip.units

import chisel3._
import chisel3.util.experimental.loadMemoryFromFile
import pkucs.carbonchip.config.ChipConfig

class FetchUnit(implicit c : ChipConfig) extends Module {
  val io = IO(new Bundle {
    val fetchAddrIn = Input(UInt(c.NumFetchAddrBits.W))
    val fetchAddrOut = Output(UInt(c.NumFetchAddrBits.W))
    val fetchValid = Output(Bool())
    val fetchInstrs = Output(Vec(c.NumFetchInstrs, UInt(c.NumInstrBits.W)))
  })

  val instrMem = SyncReadMem(c.NumMemInstrsByFetch, UInt(c.NumFetchInstrBits.W))
  loadMemoryFromFile(instrMem, "/tmp/inst.dat")

  val addr = RegNext(io.fetchAddrIn, 0.U(c.NumFetchAddrBits.W))
  io.fetchAddrOut := addr

  val instrs = Wire(UInt(c.NumFetchInstrBits.W))
  instrs := instrMem.read(io.fetchAddrIn)

  val valid = RegNext(true.B, false.B)
  io.fetchValid := valid

  for (i <- 0 until c.NumFetchInstrs) {
    io.fetchInstrs(i) := instrs(c.NumInstrBits * (i + 1) - 1, c.NumInstrBits * i)
  }
}
