package pkucs.carbonchip.basic

import chisel3._

class RegisterFile(width: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val waddr = Input(UInt(depth.W))
    val wdata = Input(UInt(width.W))

    val raddr1 = Input(UInt(depth.W))
    val rdata1 = Output(UInt(width.W))

    val raddr2 = Input(UInt(depth.W))
    val rdata2 = Output(UInt(width.W))
  })

  val mem = Mem(1L << depth, UInt(width.W))

  when(io.raddr1 === 0.U) {
    io.rdata1 := 0.U
  }.otherwise {
    io.rdata1 := mem(io.raddr1)
  }

  when(io.raddr2 === 0.U) {
    io.rdata2 := 0.U
  }.otherwise {
    io.rdata2 := mem(io.raddr2)
  }

  mem(io.waddr) := io.wdata
}
