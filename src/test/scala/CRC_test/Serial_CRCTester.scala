package CRC_test

import CRC._
import SoftwareGoldenModel._
import chisel3._
import chisel3.util._
import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}


//def reverse(s: String) : String =
//(for(i <- s.length - 1 to 0 by -1) yield s(i)).mkString


class Serial_CRCTests(c: Serial_CRC) extends PeekPokeTester(c) {


val goldenModel = new SoftwareGoldenModel()

   for (i <- 1 to 100) {
      val r1 = new scala.util.Random(i)
      val seed_int = r1.nextInt(16777215) // 2^24-1


      //val seed_string =  String.format("%24d", seed_int.toBinaryString)//.toInt.asInstanceOf[Object])
      val seed_string = seed_int.toBinaryString;
      //reverse(seed_string)
      println("Test Number " + i + ": Seed = " + seed_string)

      poke(c.io.init,true.B)
      poke(c.io.seed,seed_int)
      
      var init_res = goldenModel.Crc_sw(true, 0, seed_string)
      var out = init_res
      //var s = init_res._2
      
      poke(c.io.operand.valid,false.B)
      step(1)

      poke(c.io.init,false.B)
      step(1)
      
      for (j <- 1 to 100) 
      {
         val r2 = new scala.util.Random(j)
         val Din = r2.nextInt(1)
         println("Din = " + Din.toString)

         poke(c.io.operand.bits, Din)
         poke(c.io.operand.valid,true.B)

         step(1) // should wait 1 cycle to grab crc register result 
         
         var res = goldenModel.Crc_sw(false,Din,out)
         out = res
         //s = res._2
         var hardware_result = peek(c.io.result.bits)
         println(s"Hardware Output = ${hardware_result.toString.toInt}")

         var buf = Array.ofDim[Int](24)
         for (i <- 0 to 23)  
            buf(i) = out(i).asDigit
         //expect(c.io.result.bits, out.toInt)
         //expect(c.io.result.valid, true.B)
         step(1) 
      }
   }





// =====================================================================
// hard-coded test
/*
   poke(c.io.init,true.B)
   poke(c.io.seed,"h00000f".U)
   poke(c.io.operand.valid,false.B)
   step(1)

   poke(c.io.init,false.B)


   expect(c.io.result.bits, "hf00000".U)
   expect(c.io.result.valid, true.B)

   step(1)
   poke(c.io.init,true.B)
   poke(c.io.seed,"h000000".U)
   poke(c.io.operand.valid,false.B)

   step(1)
   poke(c.io.init,false.B)

   poke(c.io.operand.bits, "b1".U)
   poke(c.io.operand.valid,true.B)

   step(1)
   expect(c.io.result.bits, "b1101_1010_0110_0000_0000_0000".U)
   expect(c.io.result.valid, true.B)
*/



// =====================================================================
// old tests start from here
/*
   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   step(1)
   expect(c.io.result.bits, "b1"U)
   expect(c.io.result.valid, true.B)

   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   step(1)
   expect(c.io.result.bits, "b0"U)
   expect(c.io.result.valid, true.B)


   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   step(1)
   expect(c.io.result.bits, "b0"U)
   expect(c.io.result.valid, true.B)

   poke(c.io.operand.bits, "b1".U)
   poke(c.io.operand.valid,true.B)


   step(1)
   expect(c.io.result.bits, "b1"U)
   expect(c.io.result.valid, true.B)

   poke(c.io.operand.bits, "b0".U)
   poke(c.io.operand.valid,true.B)

   step(1)
   expect(c.io.result.bits, "b1"U)
   expect(c.io.result.valid, true.B)

   poke(c.io.operand.bits, "b1".U)
   poke(c.io.operand.valid,true.B)

   step(1)
   expect(c.io.result.bits, "b0"U)
   expect(c.io.result.valid, true.B)

   poke(c.io.operand.valid,false.B)

  step(1)
   expect(c.io.result.valid, false.B)
*/
   step(2)
}

class Serial_CRCTester extends ChiselFlatSpec {
   behavior of "Serial_CRC"
   backends foreach {backend =>
     it should s"perform correct operation as a Serial_CRC $backend" in { 
       Driver(() => new Serial_CRC, "verilator") { 
         (c) => new Serial_CRCTests(c)} should be (true)
    }
  }
}

