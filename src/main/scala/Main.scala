package pkucs.carbonchip

import chisel3.stage._

object CarbonChip {
  def main(args: Array[String]): Unit = {
    println((new ChiselStage)
      .emitVerilog(new basic.RegisterFile(32, 5)))
  }
}
