package pkucs.carbonchip.basic

import chisel3._
import chiseltest._
import org.scalatest._

class RegisterFileTest extends FlatSpec with ChiselScalatestTester {
  behavior of "RegisterFile"

  "the 0th register" should "never be overwritten" in {
    test(new RegisterFile(32, 5)) { c =>
      c.io.raddr1.poke(0.U)
      c.io.rdata1.expect(0.U)

      c.io.waddr.poke(0.U)
      c.io.wdata.poke(123.U)
      c.clock.step(1)

      c.io.raddr1.poke(0.U)
      c.io.rdata1.expect(0.U)
    }
  }

  "all other registers" should "be writable" in {
    test(new RegisterFile(32, 5)) { c =>
      for (i <- 1 until 32) {
        c.io.waddr.poke(i.U)
        c.io.wdata.poke((1L << i).U)
        c.clock.step(1)

        c.io.raddr1.poke(i.U)
        c.io.rdata1.expect((1L << i).U)
      }

      for (i <- 1 until 32) {
        c.io.raddr1.poke(i.U)
        c.io.rdata1.expect((1L << i).U)
      }

      for (i <- 1 until 32) {
        c.io.waddr.poke(i.U)
        c.io.wdata.poke((i + 1).U)
        c.clock.step(1)
      }

      for (i <- 1 until 32) {
        c.io.raddr1.poke(i.U)
        c.io.rdata1.expect((i + 1).U)
      }
    }
  }

  "the second read port" should "work as well" in {
    test(new RegisterFile(32, 5)) { c =>
      for (i <- 0 until 32) {
        c.io.waddr.poke(i.U)
        c.io.wdata.poke((1L << i).U)
        c.clock.step(1)
      }

      c.io.raddr2.poke(0.U)
      c.io.rdata2.expect(0.U)
      for (i <- 1 until 32) {
        c.io.raddr2.poke(i.U)
        c.io.rdata2.expect((1L << i).U)
      }
    }
  }
}
