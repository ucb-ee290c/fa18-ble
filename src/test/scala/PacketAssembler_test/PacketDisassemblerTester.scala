package PacketAssembler.test

import PacketAssembler._
import scala.collection.mutable.ArrayBuffer
import chisel3._
import chisel3.util._
import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}

/**
  * Helper functions to make testing the packet disassembler easier.
  */
object PacketDisAssemblerTestUtils {

  /**
    * Constants related to testing the packet disassembler.
    * Contains some random data, pre-preamble, preamble, and a whole BLE packet.
    */
  object Testcase {
    //hard-coded David's test
    //LSB-MSB
    val wholepacket_dig = "b01101011011111011001000101110001_0100000000001000_011000110000000101001100010011100100000000000000_01000000100000001010000001100000000100001100101011000010101010101011001011001100".U
    val wholepacket_rad = "b01101011011111011001000101110001_1111000101000011_100010011000010011110000101010110010011000001101_11101110000011000010100001110010011110010010010011011101011011011101000001011011".U
    //---------------------_--------------AA----------------_---PDU header---_-----------PDU advertiser address---------------_-----------------PDU data1 and data2--------------------------------------------
    val pre_preamble = "b000111".U
    //pre-preamble
    val preamble = "b01010101".U
    val AA = "b01101011011111011001000101110001".U
    val CRC_rad = "b001101000100101101101110".U

    //MSB-LSB
    val wholepacket_dig_rev = "b00110011010011010101010101000011010100110000100000000110000001010000000100000010000000000000001001110010001100101000000011000110000100000000001010001110100010011011111011010110".U
    val wholepacket_rad_rev = "b11011010000010111011011010111011001001001001111001001110000101000011000001110111101100000110010011010101000011110010000110010001110000101000111110001110100010011011111011010110".U

    val random_sequence_rev = "hf7293742343023801b".U
    //72 bits//note not to poke preamble here
    val pre_preamble_rev = "b111000".U
    val preamble_rev = "b10101010".U
    val AA_rev = "b10001110100010011011111011010110".U
    val CRC_rad_rev = "b011101101101001000101100".U

    val CRC_Seed = "b010101010101010101010101".U
    val DeWhite_Seed = "b1100101".U
  }

  /**
    * Write a given number of bits to the given FIFO.
    * @param tester PeekPokeTester to use
    * @param fifo A Decoupled(UInt(1.W))
    * @param data UInt literal containing data to write
    * @param numBits Number of bits to write
    */
  def writeBitsToFIFO[T <: Module](tester: PeekPokeTester[T], fifo: DecoupledIO[UInt], data: UInt, numBits: Int): Unit = {
    for (j <- 0 to numBits - 1) {
      // Wait for FIFO to become ready
      var count = 0
      while (tester.peek(fifo.ready) == 0) {
        if (count > 100) {
          println("Taking too long to become ready")
          tester.finish
        }
        tester.step(1)
        count += 1
      }
      tester.poke(fifo.valid, 1)
      tester.poke(fifo.bits, data(j).litValue)
      tester.step(1)
    }
    tester.poke(fifo.valid, 0)
  }

  /**
    * Write a given range of bits to the FIFO.
    * @param tester PeekPokeTester to use
    * @param fifo A Decoupled(UInt(1.W))
    * @param writeData UInt literal containing data to write
    * @param startBit Bit to start at (e.g. 0), inclusive
    * @param endBit Bit to end at (e.g. 7), inclusive
    * @param outputByteFifo Output byte FIFO to read from to check
    * @param checkData Data with which to check output byte FIFO
    */
  def writeBitsToFIFOAndCheck[T <: Module](tester: PeekPokeTester[T],
                                           fifo: DecoupledIO[UInt], writeData: UInt,
                                           startBit: Int, endBit: Int,
                                           outputByteFifo: DecoupledIO[UInt], checkData: UInt): Unit = {
    // Keep track of received vs. expected outputs separately since there may be some delays
    // between when the output is sent vs received.
    val receivedOutputs: ArrayBuffer[BigInt] = ArrayBuffer()
    val expectedOutputs: ArrayBuffer[BigInt] = ArrayBuffer()
    val js: ArrayBuffer[Int] = ArrayBuffer()

    require(endBit >= startBit)
    for (j <- startBit to endBit) {
      // Wait for FIFO to become ready
      while (tester.peek(fifo.ready) == 0) {
        tester.step(1)
      }
      tester.poke(fifo.valid, 1)
      tester.poke(fifo.bits, writeData(j).litValue)
      tester.step(1)
      if (tester.peek(outputByteFifo.valid) == 1) {
        receivedOutputs += tester.peek(outputByteFifo.bits)
      }
      if (j % 8 == 7) {
        val byte = checkData((j / 8) * 8 + 7, (j / 8) * 8)
        expectedOutputs += byte.litValue
        js += j
      }
    }
    tester.poke(fifo.valid, 0)

    // Check the outputs
    while (receivedOutputs.length < expectedOutputs.length) {
      tester.step(1)
      if (tester.peek(outputByteFifo.valid) == 1) {
        receivedOutputs += tester.peek(outputByteFifo.bits)
      }
    }
    (js zip receivedOutputs zip expectedOutputs).foreach {
      case ((j, expected), received) => {
        println(s"j=$j\n$received\t${expected}")
        assert(expected == received)
      }
    }
  }

  /**
    * Set some constants that need to be in place throughout the whole test.
    */
  def setRegisterConstants[T <: Module](tester: PeekPokeTester[T],
                                        aa: UInt, aaConst: BigInt,
                                        crcSeed: UInt, crcSeedConst: BigInt,
                                        dewhiteSeed: UInt, dewhiteSeedConst: BigInt): Unit = {
    tester.poke(aa, aaConst)
    tester.poke(crcSeed, crcSeedConst)
    tester.poke(dewhiteSeed, dewhiteSeedConst)
  }
}

class PacketDisAssemblerTest(c: PacketDisAssembler) extends PeekPokeTester(c) {
  import PacketDisAssemblerTestUtils.Testcase

  // Constants that remain throughout packet disassembly
  PacketDisAssemblerTestUtils.setRegisterConstants(this,
    c.io.REG_AA_i, Testcase.AA_rev.litValue,
    c.io.REG_CRC_Seed_i, Testcase.CRC_Seed.litValue,
    c.io.REG_DeWhite_Seed_i, Testcase.DeWhite_Seed.litValue
  )

  //initialize
  poke(c.io.DMA_Switch_i, false.B)

  poke(c.io.DMA_Data_o.ready, false.B)
  poke(c.io.DMA_Length_o.ready, false.B)
  poke(c.io.DMA_Flag_AA_o.ready, false.B)
  poke(c.io.DMA_Flag_CRC_o.ready, false.B)

  poke(c.io.AFIFO_Data_i.valid, false.B)
  poke(c.io.AFIFO_Data_i.bits, 0.U)

  step(2)

  //start of receiving packet
  poke(c.io.DMA_Switch_i, true.B)

  poke(c.io.DMA_Data_o.ready, true.B)
  poke(c.io.DMA_Length_o.ready, true.B)
  poke(c.io.DMA_Flag_AA_o.ready, true.B)
  poke(c.io.DMA_Flag_CRC_o.ready, true.B)

  poke(c.io.AFIFO_Data_i.valid, false.B)
  poke(c.io.AFIFO_Data_i.bits, 0.U)

  // Random sequence before pre_preamble
  PacketDisAssemblerTestUtils.writeBitsToFIFO(this, c.io.AFIFO_Data_i, data = Testcase.random_sequence_rev, numBits = 72)

  // pre_preamble
  PacketDisAssemblerTestUtils.writeBitsToFIFO(this, c.io.AFIFO_Data_i, data = Testcase.pre_preamble_rev, numBits = 6)
  expect(c.io.DMA_Data_o.valid, false.B) //note
  println(s"after random_sequence\n${peek(c.io.DMA_Data_o.bits)}\t0.U")

  // PREAMBLE
  PacketDisAssemblerTestUtils.writeBitsToFIFO(this, c.io.AFIFO_Data_i, data = Testcase.preamble_rev, numBits = 8)
  expect(c.io.DMA_Data_o.valid, false.B) //note
  println(s"after preamble\n${peek(c.io.DMA_Data_o.valid)}\tfalse")

  // AA
  PacketDisAssemblerTestUtils.writeBitsToFIFOAndCheck(this, fifo = c.io.AFIFO_Data_i, writeData = Testcase.wholepacket_rad_rev,
    startBit = 0, endBit = 31,
    outputByteFifo = c.io.DMA_Data_o, checkData = Testcase.wholepacket_dig_rev)
  step(1) // need an extra cycle for DMA_Flag_AA_o to be valid
  expect(c.io.DMA_Flag_AA_o.bits, false.B)
  expect(c.io.DMA_Flag_AA_o.valid, true.B)
  println(s"j=flagAA\n${peek(c.io.DMA_Flag_AA_o.bits)}\ttrue")
  println(s"j=flagAA\n${peek(c.io.DMA_Flag_AA_o.valid)}\ttrue")

  // PDU_HEADER
  PacketDisAssemblerTestUtils.writeBitsToFIFOAndCheck(this, fifo = c.io.AFIFO_Data_i, writeData = Testcase.wholepacket_rad_rev,
    startBit = 32, endBit = 47,
    outputByteFifo = c.io.DMA_Data_o, checkData = Testcase.wholepacket_dig_rev)
  step(1) // need an extra cycle before DMA_Length_o is valid
  expect(c.io.DMA_Length_o.bits, 16.U)
  expect(c.io.DMA_Length_o.valid, true.B)
  println(s"j=DMA_Length_o\n${peek(c.io.DMA_Length_o.bits)}\t16.U")

  step(1)
  poke(c.io.DMA_Length_o.ready, false.B)

  // PDU_PAYLOAD
  PacketDisAssemblerTestUtils.writeBitsToFIFOAndCheck(this, fifo = c.io.AFIFO_Data_i, writeData = Testcase.wholepacket_rad_rev,
    startBit = 48, endBit = 22 * 8 - 1,
    outputByteFifo = c.io.DMA_Data_o, checkData = Testcase.wholepacket_dig_rev)

  // CRC
  PacketDisAssemblerTestUtils.writeBitsToFIFO(this, c.io.AFIFO_Data_i, data = Testcase.CRC_rad_rev, numBits = 24)

  step(2)
  expect(c.io.DMA_Flag_CRC_o.bits, false.B)
  expect(c.io.DMA_Flag_CRC_o.valid, true.B)
  println(s"j=flagCRC_bits\n${peek(c.io.DMA_Flag_CRC_o.bits)}\tfalse")
  println(s"j=flagCRC_valid\n${peek(c.io.DMA_Flag_CRC_o.valid)}\ttrue")

  step(2)

  poke(c.io.DMA_Flag_AA_o.ready, false.B)
  poke(c.io.DMA_Flag_CRC_o.ready, false.B)


  //todo: add FIFO
  //todo: add invalid DMA
  //todo: check output: DMA_ready

  //todo: AA, CRC correct
  //todo: DMA_Switch_i OFF
  //todo: ready, valid always ON
}

class PacketDisAssemblerTester extends ChiselFlatSpec {
  behavior of "PacketDisAssembler"
  backends foreach { backend =>
    it should s"perform correct operation as an packet disassembler $backend" in {
      Driver(() => new PacketDisAssembler, "verilator") {
        (c) => new PacketDisAssemblerTest(c)
      } should be(true)
    }
  }
}
