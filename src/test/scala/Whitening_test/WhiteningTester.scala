package Whitening_test

import SoftwareGoldenModel._
import Whitening._
import chisel3._
import chisel3.util._
import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}


class WhiteningTests(c: Whitening) extends PeekPokeTester(c) {

   val goldenModel = new SoftwareGoldenModel()

   for (i <- 1 to 100) {
      val r1 = new scala.util.Random(i)
      val seed_int = r1.nextInt(127)
      val seed_string =  String.format("%07d", seed_int.toBinaryString.toInt.asInstanceOf[Object])
      println("Test Number " + i + ": Seed = " + seed_string)

      poke(c.io.init,true.B)
      poke(c.io.seed,seed_int)
      
      var init_res = goldenModel.Whitening_sw(true,0,seed_string)
      var out = init_res._1
      var s = init_res._2
      
      poke(c.io.operand.valid,false.B)
      step(1)

      poke(c.io.init,false.B)
      step(1)
      
      for (j <- 1 to 100) {
         val r2 = new scala.util.Random(j)
         val Din = r2.nextInt(1)
         println("Din = " + Din.toString)

         poke(c.io.operand.bits, Din)
         poke(c.io.operand.valid,true.B)
         var res = goldenModel.Whitening_sw(false,Din,s)
         out = res._1
         s = res._2
         println(s"Hardware Output = ${peek(c.io.result.bits)}")

         expect(c.io.result.bits, out)
         expect(c.io.result.valid, true.B)
         step(1)
      

      }
   }
}
   //while (peek(c.io.operand.ready) == BigInt(0)) {
   //   step(1)
   //}
   /*
poke(c.io.operand.ready,true.B)
step(1)

   poke(c.io.operand.bits, "ha1".U)
   poke(c.io.operand.valid,true.B)
   poke(c.io.operand.bits, "ha1".U)
   step(1)

   poke(c.io.operand.valid,false.B)
*/
   //while (peek(c.io.result.valid) == BigInt(0)) {
   //   step(1)
   //}

   // poke(c.io.result.ready,true.B)                  The 20:1 FIFO will always be ready, so the Whitening module doesn't depend on the result.ready signal.
  



/*
   poke(c.io.operand.bits, "b1".U)
   poke(c.io.operand.valid,true.B)

   expect(c.io.result.bits, "b0"U)
   expect(c.io.result.valid, true.B)
   step(1)

   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   expect(c.io.result.bits, "b1"U)
   expect(c.io.result.valid, true.B)

   step(1)
   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   expect(c.io.result.bits, "b1"U)
   expect(c.io.result.valid, true.B)

   step(1)
   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   expect(c.io.result.bits, "b0"U)
   expect(c.io.result.valid, true.B)

   step(1)

   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   expect(c.io.result.bits, "b0"U)
   expect(c.io.result.valid, true.B)

   step(1)
   poke(c.io.operand.bits, "b1".U)
   poke(c.io.operand.valid,true.B)


   expect(c.io.result.bits, "b1"U)
   expect(c.io.result.valid, true.B)

   step(1)
   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   expect(c.io.result.bits, "b1"U)
   expect(c.io.result.valid, true.B)

   step(1)
   poke(c.io.operand.bits, "b1".U)
   poke(c.io.operand.valid,true.B)

   expect(c.io.result.bits, "b0"U)
   expect(c.io.result.valid, true.B)

   step(1)
   poke(c.io.operand.valid,false.B)

   expect(c.io.result.valid, false.B)

   step(2)
   
/*
   while (peek(c.io.operand.ready) == BigInt(0)) {             // wait for the Whitening module is ready to take next byte of data
      step(1)
   }

   poke(c.io.operand.valid, true.B)
   poke(c.io.operand.bits, "h03".U)

   step(1)
   poke(c.io.operand.valid, false.B)

   while (peek(c.io.result.valid) == BigInt(0)) {             // wait for the result is valid
      step(1)
   }

   expect(c.io.result.valid, true.B)
   expect(c.io.result.bits, "b0".U)

   step(1)
   expect(c.io.result.valid, true.B)
   expect(c.io.result.bits, "b1".U)

   step(1)
   expect(c.io.result.valid, true.B)
   expect(c.io.result.bits, "b1".U)

   step(1)
   expect(c.io.result.valid, true.B)
   expect(c.io.result.bits, "b1".U)

   step(1)
   expect(c.io.result.valid, true.B)
   expect(c.io.result.bits, "b0".U)

   step(1)
   expect(c.io.result.valid, true.B)
   expect(c.io.result.bits, "b0".U)

   step(1)
   expect(c.io.result.valid, true.B)
   expect(c.io.result.bits, "b0".U)

   step(1)
   expect(c.io.result.valid, true.B)
   expect(c.io.result.bits, "b1".U)

   step(1)
   expect(c.io.result.valid, false.B)
   expect(c.io.operand.ready, true.B)

   step(2)
   poke(c.io.end, true.B)
   expect(c.io.operand.ready, true.B)
   
   step(1)
*/}
*/
class WhiteningTester extends ChiselFlatSpec {
   behavior of "Whitening"
   backends foreach {backend =>
     it should s"perform correct operation as an Whitening $backend" in { 
       Driver(() => new Whitening, "verilator") { 
         (c) => new WhiteningTests(c)} should be (true)
    }
  }
}

