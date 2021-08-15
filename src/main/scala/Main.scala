package pkucs.carbonchip

import chisel3.stage._
import pkucs.carbonchip.config.ChipConfig

object Main {
  def main(args: Array[String]): Unit = {
    implicit val c = new ChipConfig
    println((new ChiselStage)
      .execute(args, Seq(new ChiselGeneratorAnnotation(() => new CarbonChip()))))
  }
}
